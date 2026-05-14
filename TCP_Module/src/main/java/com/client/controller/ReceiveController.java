package com.client.controller;

import com.client.controller.dto.AcceptTransferRequest;
import com.client.service.ClientTransferService;
import com.client.service.TransferTaskRegistry;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.file.RetransmitRequestPacket;
import com.session.TransferDirection;
import com.session.TransferTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class ReceiveController {

    private final ClientTransferService clientTransferService;
    private final TransferTaskRegistry transferTaskRegistry;

    public ReceiveController(
            ClientTransferService clientTransferService,
            TransferTaskRegistry transferTaskRegistry
    ) {
        this.clientTransferService = clientTransferService;
        this.transferTaskRegistry = transferTaskRegistry;
    }

    @GetMapping({"/incoming", "/api/receive/incoming"})
    public ResponseEntity<List<Map<String, Object>>> listIncomingRequests() {
        Map<String, IncomingTransferRequestPacket> requests = clientTransferService.pendingIncomingTransferRequests();
        List<Map<String, Object>> result = requests.values().stream()
                .map(request -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("transferId", request.getTransferId());
                    item.put("senderDeviceId", request.getSenderDeviceId());
                    item.put("fileName", request.getFileName());
                    item.put("fileSize", request.getFileSize());
                    item.put("totalBlocks", request.getTotalBlocks());
                    return item;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping({"/accept", "/api/receive/accept"})
    public ResponseEntity<Map<String, Object>> acceptTransfer(@RequestBody AcceptTransferRequest request) {
        if (request == null || request.getTransferId() == null || request.getTransferId().isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "transferId is required");
            return ResponseEntity.badRequest().body(error);
        }

        clientTransferService.acceptIncomingTransfer(request.getTransferId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", request.getTransferId());
        payload.put("message", "Accepted incoming transfer request");
        return ResponseEntity.ok(payload);
    }

    @PostMapping({"/reject", "/api/receive/reject"})
    public ResponseEntity<Map<String, Object>> rejectTransfer(@RequestBody Map<String, String> request) {
        if (request == null) {
            return badRequest("transferId is required");
        }
        String transferId = request.get("transferId");
        if (transferId == null || transferId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "transferId is required");
            return ResponseEntity.badRequest().body(error);
        }

        clientTransferService.rejectIncomingTransfer(transferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", transferId);
        payload.put("message", "Rejected incoming transfer request");
        return ResponseEntity.ok(payload);
    }

    @PostMapping({"/retransmit", "/api/receive/retransmit"})
    public ResponseEntity<Map<String, Object>> requestRetransmission(@RequestBody Map<String, String> request) {
        if (request == null) {
            return badRequest("taskIdOrTransferId or transferId is required");
        }
        String taskIdOrTransferId = request.get("taskIdOrTransferId");
        if (taskIdOrTransferId == null || taskIdOrTransferId.isBlank()) {
            taskIdOrTransferId = request.get("transferId");
        }
        if (taskIdOrTransferId == null || taskIdOrTransferId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "taskIdOrTransferId or transferId is required");
            return ResponseEntity.badRequest().body(error);
        }

        clientTransferService.requestRetransmission(taskIdOrTransferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("taskIdOrTransferId", taskIdOrTransferId);
        payload.put("message", "Retransmission requested");
        return ResponseEntity.ok(payload);
    }

    @GetMapping({"/retransmit-requests", "/api/receive/retransmit-requests"})
    public ResponseEntity<List<Map<String, Object>>> listRetransmissionRequests() {
        List<Map<String, Object>> result = clientTransferService.pendingRetransmitRequests().values().stream()
                .map(this::retransmitRequestPayload)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping({"/retransmit-accept", "/api/receive/retransmit-accept"})
    public ResponseEntity<Map<String, Object>> acceptRetransmission(@RequestBody Map<String, String> request) {
        String transferId = transferIdFrom(request);
        if (transferId == null) {
            return badRequest("transferId is required");
        }

        clientTransferService.acceptRetransmission(transferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", transferId);
        payload.put("message", "Retransmission accepted");
        return ResponseEntity.ok(payload);
    }

    @PostMapping({"/retransmit-reject", "/api/receive/retransmit-reject"})
    public ResponseEntity<Map<String, Object>> rejectRetransmission(@RequestBody Map<String, String> request) {
        String transferId = transferIdFrom(request);
        if (transferId == null) {
            return badRequest("transferId is required");
        }

        clientTransferService.rejectRetransmission(transferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", transferId);
        payload.put("message", "Retransmission rejected");
        return ResponseEntity.ok(payload);
    }

    @PostMapping({"/open-folder", "/open-received", "/api/receive/open-folder", "/api/receive/open-received"})
    public ResponseEntity<Map<String, Object>> openFileFolder(@RequestBody Map<String, String> request) {
        if (request == null) {
            return badRequest("taskId, transferId, taskIdOrTransferId, target, or fileName is required");
        }

        String target = firstNonBlank(
                request.get("taskIdOrTransferId"),
                request.get("taskId"),
                request.get("transferId"),
                request.get("target"),
                request.get("fileName")
        );
        if (target == null) {
            return badRequest("taskId, transferId, taskIdOrTransferId, target, or fileName is required");
        }

        TransferTask task;
        try {
            task = resolveReceivedFileTask(target);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
        if (task == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Received file not found by taskId, transferId, or fileName: " + target);
            return ResponseEntity.status(404).body(error);
        }

        String localPath = task.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Local file path not available");
            return ResponseEntity.ok(error);
        }

        Path filePath = Paths.get(localPath).toAbsolutePath().normalize();
        if (!Files.exists(filePath)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "File does not exist: " + localPath);
            return ResponseEntity.ok(error);
        }

        try {
            revealInFileManager(filePath);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("taskId", task.getTaskId());
            payload.put("transferId", task.getTransferId());
            payload.put("filePath", filePath.toString());
            payload.put("folderPath", filePath.getParent() == null ? filePath.toString() : filePath.getParent().toString());
            payload.put("message", "File location opened successfully");
            return ResponseEntity.ok(payload);

        } catch (IOException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Failed to open file location: " + e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    private Map<String, Object> retransmitRequestPayload(RetransmitRequestPacket request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("transferId", request.getTransferId());
        item.put("startBlockId", request.getStartBlockId());
        item.put("reason", request.getReason());
        return item;
    }

    private TransferTask resolveReceivedFileTask(String target) {
        TransferTask task = transferTaskRegistry.findByTaskId(target)
                .or(() -> transferTaskRegistry.findByTransferId(target))
                .orElse(null);
        if (task != null) {
            if (task.getDirection() != TransferDirection.RECEIVE) {
                throw new IllegalArgumentException("Task is not a received file: " + target);
            }
            return task;
        }

        List<TransferTask> matches = transferTaskRegistry.allTasks().stream()
                .filter(candidate -> candidate.getDirection() == TransferDirection.RECEIVE)
                .filter(candidate -> fileNameMatches(candidate, target))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Multiple received files matched. Please use taskId or transferId.");
        }
        return matches.get(0);
    }

    private boolean fileNameMatches(TransferTask task, String target) {
        List<String> names = new ArrayList<>();
        if (task.getFileName() != null && !task.getFileName().isBlank()) {
            names.add(task.getFileName());
        }
        if (task.getLocalPath() != null && !task.getLocalPath().isBlank()) {
            Path fileName = Paths.get(task.getLocalPath()).getFileName();
            if (fileName != null) {
                names.add(fileName.toString());
            }
        }
        return names.stream().anyMatch(name -> name.equals(target) || name.equalsIgnoreCase(target));
    }

    private void revealInFileManager(Path filePath) throws IOException {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder processBuilder;
        if (osName.contains("mac")) {
            processBuilder = new ProcessBuilder("open", "-R", filePath.toString());
        } else if (osName.contains("win")) {
            processBuilder = new ProcessBuilder("explorer.exe", "/select," + filePath);
        } else {
            Path parent = filePath.getParent();
            processBuilder = new ProcessBuilder("xdg-open", parent == null ? filePath.toString() : parent.toString());
        }
        processBuilder.start();
    }

    private String transferIdFrom(Map<String, String> request) {
        if (request == null) {
            return null;
        }
        return firstNonBlank(request.get("transferId"), request.get("taskIdOrTransferId"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}
