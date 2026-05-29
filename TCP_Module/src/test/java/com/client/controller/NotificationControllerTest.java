package com.client.controller;

import com.common.service.PushNotificationService;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NotificationControllerTest
{
    @Test
    public void eventsReturnsSseEmitter()
    {
        NotificationController controller = new NotificationController(new PushNotificationService());

        ResponseEntity<SseEmitter> response = controller.events();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }
}
