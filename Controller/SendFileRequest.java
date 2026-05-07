package com.controller;

import com.controller.dto.SendFileRequest;
import com.service.ClientTransferService;
import com.service.TransferTaskRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/send")
public class SendController
{
    private final ClientTransferService clientTransferService;
    private final TransferTaskRegistry transferTaskRegistry;

    public SendController(
            ClientTransferService clientTransferService,
            TransferTaskRegistry transferTaskRegistry
    )
    {
        this.clientTransferService = clientTransferService;
        this.transferTaskRegistry = transferTaskRegistry;
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

        String taskId = clientTransferService.sendFile(Path.of(request.getFilePath()), request.getTargetAccountId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("taskId", taskId);
        payload.put("message", "Send task created");
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> listTasks()
    {
        List<Map<String, Object>> tasks = transferTaskRegistry.allTasks().stream()
                .map(task -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("taskId", task.getTaskId());
                    item.put("transferId", task.getTransferId());
                    item.put("direction", task.getDirection());
                    item.put("status", task.getStatus());
                    item.put("fileName", task.getFileName());
                    item.put("progress", task.getProgress() * 100D);
                    item.put("message", task.getMessage());
                    return item;
                })
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
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Task not found");
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getTaskId());
        payload.put("transferId", task.getTransferId());
        payload.put("direction", task.getDirection());
        payload.put("status", task.getStatus());
        payload.put("fileName", task.getFileName());
        payload.put("localPath", task.getLocalPath());
        payload.put("peerDeviceId", task.getPeerDeviceId());
        payload.put("transferredBytes", task.getTransferredBytes());
        payload.put("totalBytes", task.getTotalBytes());
        payload.put("transferredBlocks", task.getTransferredBlocks());
        payload.put("totalBlocks", task.getTotalBlocks());
        payload.put("progress", task.getProgress() * 100D);
        payload.put("createdAt", task.getCreatedAt());
        payload.put("message", task.getMessage());
        return ResponseEntity.ok(payload);
    }
}