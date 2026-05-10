package com.client.controller;

import com.client.controller.dto.SendFileRequest;
import com.client.service.ClientTransferService;
import com.client.service.LocalContactBookService;
import com.client.service.TransferTaskRegistry;
import com.common.util.PathInputNormalizer;
import com.session.TransferStatus;
import com.session.TransferTask;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/send")
public class SendController
{
    private static final long TASK_STREAM_INTERVAL_MILLIS = 1000L;

    private final ClientTransferService clientTransferService;
    private final LocalContactBookService localContactBookService;
    private final TransferTaskRegistry transferTaskRegistry;
    private final ExecutorService taskStreamExecutor = Executors.newCachedThreadPool();

    public SendController(
            ClientTransferService clientTransferService,
            LocalContactBookService localContactBookService,
            TransferTaskRegistry transferTaskRegistry
    )
    {
        this.clientTransferService = clientTransferService;
        this.localContactBookService = localContactBookService;
        this.transferTaskRegistry = transferTaskRegistry;
    }

    @PreDestroy
    public void shutdownTaskStreamExecutor()
    {
        taskStreamExecutor.shutdownNow();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> sendFile(@RequestBody SendFileRequest request)
    {
        if (request.getFilePath() == null || request.getFilePath().isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        if (request.getTargetAccountId() == null || request.getTargetAccountId().isBlank()) {
            throw new IllegalArgumentException("targetAccountId is required");
        }

        String targetAccountId = localContactBookService.resolveAccountId(request.getTargetAccountId());
        String taskId = clientTransferService.sendFile(PathInputNormalizer.toPath(request.getFilePath()), targetAccountId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("taskId", taskId);
        payload.put("targetAccountId", targetAccountId);
        payload.put("message", "Send task created");
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> listTasks()
    {
        List<Map<String, Object>> tasks = transferTaskRegistry.allTasks().stream()
                .map(this::taskSummaryPayload)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/tasks/{taskIdOrTransferId}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String taskIdOrTransferId)
    {
        var task = transferTaskRegistry.findByTaskId(taskIdOrTransferId)
                .or(() -> transferTaskRegistry.findByTransferId(taskIdOrTransferId))
                .orElse(null);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(taskDetailPayload(task));
    }

    @PostMapping("/tasks/{taskIdOrTransferId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskIdOrTransferId)
    {
        clientTransferService.cancelTransfer(taskIdOrTransferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("taskIdOrTransferId", taskIdOrTransferId);
        payload.put("message", "Transfer canceled");
        return ResponseEntity.ok(payload);
    }

    @GetMapping(path = "/tasks/{taskIdOrTransferId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamTask(@PathVariable String taskIdOrTransferId)
    {
        TransferTask task = findTask(taskIdOrTransferId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        SseEmitter emitter = new SseEmitter(0L);
        taskStreamExecutor.submit(() -> streamTaskProgress(task, emitter));
        return ResponseEntity.ok(emitter);
    }

    private void streamTaskProgress(TransferTask task, SseEmitter emitter)
    {
        try {
            while (true) {
                String eventName = isTerminal(task.getStatus()) ? "complete" : "progress";
                emitter.send(SseEmitter.event().name(eventName).data(taskDetailPayload(task)));

                if (isTerminal(task.getStatus())) {
                    emitter.complete();
                    return;
                }

                Thread.sleep(TASK_STREAM_INTERVAL_MILLIS);
            }
        } catch (IOException ex) {
            emitter.complete();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            emitter.complete();
        } catch (Exception ex) {
            emitter.completeWithError(ex);
        }
    }

    private TransferTask findTask(String taskIdOrTransferId)
    {
        return transferTaskRegistry.findByTaskId(taskIdOrTransferId)
                .or(() -> transferTaskRegistry.findByTransferId(taskIdOrTransferId))
                .orElse(null);
    }

    private Map<String, Object> taskSummaryPayload(TransferTask task)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getTaskId());
        item.put("transferId", task.getTransferId());
        item.put("direction", task.getDirection());
        item.put("status", task.getStatus());
        item.put("fileName", task.getFileName());
        item.put("progress", task.getProgress() * 100D);
        item.put("message", task.getMessage());
        return item;
    }

    private Map<String, Object> taskDetailPayload(TransferTask task)
    {
        Map<String, Object> payload = taskSummaryPayload(task);
        payload.put("localPath", task.getLocalPath());
        payload.put("peerDeviceId", task.getPeerDeviceId());
        payload.put("transferredBytes", task.getTransferredBytes());
        payload.put("totalBytes", task.getTotalBytes());
        payload.put("transferredBlocks", task.getTransferredBlocks());
        payload.put("totalBlocks", task.getTotalBlocks());
        payload.put("createdAt", task.getCreatedAt());
        return payload;
    }

    private boolean isTerminal(TransferStatus status)
    {
        return status != null && status.isTerminal();
    }
}
