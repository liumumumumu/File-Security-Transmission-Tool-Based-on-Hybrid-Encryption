package com.client.controller;

import com.client.message.ClientMessageService;
import com.client.message.ConversationSummary;
import com.client.message.TextMessageRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController
{
    private final ClientMessageService clientMessageService;

    public MessageController(ClientMessageService clientMessageService)
    {
        this.clientMessageService = clientMessageService;
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, String> request)
    {
        String targetAccountId = request == null ? null : request.get("targetAccountId");
        String text = request == null ? null : request.get("text");
        TextMessageRecord record = clientMessageService.sendRelay(targetAccountId, text);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("messageId", record.getMessageId());
        payload.put("targetAccountId", record.getPeerAccountId());
        payload.put("status", record.getStatus());
        return ResponseEntity.ok(payload);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> summaries()
    {
        return ResponseEntity.ok(clientMessageService.summaries().stream()
                .map(this::summaryPayload)
                .toList());
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<List<Map<String, Object>>> conversation(@PathVariable String accountId)
    {
        return ResponseEntity.ok(clientMessageService.conversation(accountId, true).stream()
                .map(this::messagePayload)
                .toList());
    }

    private Map<String, Object> summaryPayload(ConversationSummary summary)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("peerAccountId", summary.peerAccountId());
        payload.put("alias", summary.alias());
        payload.put("unreadCount", summary.unreadCount());
        payload.put("lastMessageTime", summary.lastMessageTime());
        payload.put("lastDirection", summary.lastDirection());
        payload.put("lastStatus", summary.lastStatus());
        payload.put("lastMode", summary.lastMode());
        return payload;
    }

    private Map<String, Object> messagePayload(TextMessageRecord record)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", record.getMessageId());
        payload.put("peerAccountId", record.getPeerAccountId());
        payload.put("senderAccountId", record.getSenderAccountId());
        payload.put("receiverAccountId", record.getReceiverAccountId());
        payload.put("direction", record.getDirection());
        payload.put("mode", record.getMode());
        payload.put("status", record.getStatus());
        payload.put("createdAt", record.getCreatedAt());
        payload.put("receivedAt", record.getReceivedAt());
        payload.put("readAt", record.getReadAt());
        payload.put("body", record.getBody());
        payload.put("errorMessage", record.getErrorMessage());
        return payload;
    }
}
