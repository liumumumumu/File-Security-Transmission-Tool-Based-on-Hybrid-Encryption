package com.client.controller;

import com.common.service.PushNotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class NotificationController
{
    private final PushNotificationService pushNotificationService;

    public NotificationController(PushNotificationService pushNotificationService)
    {
        this.pushNotificationService = pushNotificationService;
    }

    @GetMapping(path = {"/api/events", "/api/notifications/events"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> events()
    {
        SseEmitter emitter = pushNotificationService.subscribe();
        try
        {
            emitter.send(SseEmitter.event().name("connected").data(connectedPayload()));
        }
        catch(IOException ex)
        {
            emitter.completeWithError(ex);
        }
        return ResponseEntity.ok(emitter);
    }

    private Map<String, Object> connectedPayload()
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "connected");
        payload.put("timestamp", Instant.now().toString());
        payload.put("payload", Map.of(
                "message", "Notification stream connected"
        ));
        return payload;
    }
}
