package com.client.controller;

import com.client.controller.dto.AcceptTransferRequest;
import com.client.service.ClientTransferService;
import com.client.service.TransferTaskRegistry;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.session.TransferTask;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
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

    @GetMapping("/incoming")
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

    @PostMapping("/accept")
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

    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> rejectTransfer(@RequestBody Map<String, String> request) {
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

    @PostMapping("/retransmit")
    public ResponseEntity<Map<String, Object>> requestRetransmission(@RequestBody Map<String, String> request) {
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

    @PostMapping("/open-folder")
    public ResponseEntity<Map<String, Object>> openFileFolder(@RequestBody Map<String, String> request) {
        String taskIdOrTransferId = request.get("taskId");
        if (taskIdOrTransferId == null || taskIdOrTransferId.isBlank()) {
            taskIdOrTransferId = request.get("transferId");
        }
        if (taskIdOrTransferId == null || taskIdOrTransferId.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "taskId or transferId is required");
            return ResponseEntity.badRequest().body(error);
        }

        final String finalTaskId = taskIdOrTransferId;
        TransferTask task = transferTaskRegistry.findByTaskId(finalTaskId)
                .or(() -> transferTaskRegistry.findByTransferId(finalTaskId))
                .orElse(null);

        if (task == null) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Task not found");
            return ResponseEntity.notFound().build();
        }

        String localPath = task.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Local file path not available");
            return ResponseEntity.ok(error);
        }

        Path filePath = Paths.get(localPath);
        File file = filePath.toFile();

        if (!file.exists()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "File does not exist: " + localPath);
            return ResponseEntity.ok(error);
        }

        File parentFolder = file.getParentFile();
        if (parentFolder == null) {
            parentFolder = file;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(parentFolder);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("explorer.exe", parentFolder.getAbsolutePath());
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", parentFolder.getAbsolutePath());
                } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
                    pb = new ProcessBuilder("xdg-open", parentFolder.getAbsolutePath());
                } else {
                    Map<String, Object> error = new LinkedHashMap<>();
                    error.put("success", false);
                    error.put("message", "Unsupported operating system");
                    return ResponseEntity.ok(error);
                }
                pb.start();
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("folderPath", parentFolder.getAbsolutePath());
            payload.put("message", "Folder opened successfully");
            return ResponseEntity.ok(payload);

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "Failed to open folder: " + e.getMessage());
            return ResponseEntity.ok(error);
        }
    }
}