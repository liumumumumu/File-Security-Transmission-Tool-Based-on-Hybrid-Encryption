package com.controller;

import com.controller.dto.AcceptTransferRequest;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.service.ClientTransferService;
import com.service.TransferTaskRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receive")
public class ReceiveController
{
    private final ClientTransferService clientTransferService;
    private final TransferTaskRegistry transferTaskRegistry;

    public ReceiveController(
            ClientTransferService clientTransferService,
            TransferTaskRegistry transferTaskRegistry
    )
    {
        this.clientTransferService = clientTransferService;
        this.transferTaskRegistry = transferTaskRegistry;
    }

    @GetMapping("/incoming")
    public ResponseEntity<List<Map<String, Object>>> listIncomingRequests()
    {
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
    public ResponseEntity<Map<String, Object>> acceptTransfer(@RequestBody AcceptTransferRequest request)
    {
        if (request == null || request.getTransferId() == null || request.getTransferId().isBlank()) {
            throw new IllegalArgumentException("transferId is required");
        }

        clientTransferService.acceptIncomingTransfer(request.getTransferId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", request.getTransferId());
        payload.put("message", "Accepted incoming transfer request");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/reject")
    public ResponseEntity<Map<String, Object>> rejectTransfer(@RequestBody Map<String, String> request)
    {
        String transferId = request.get("transferId");
        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException("transferId is required");
        }

        clientTransferService.rejectIncomingTransfer(transferId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("transferId", transferId);
        payload.put("message", "Rejected incoming transfer request");
        return ResponseEntity.ok(payload);
    }
}