package com.client.controller;

import com.client.ApplicationShutdownService;
import com.client.service.PrivateKeyArtifactService;
import org.junit.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SystemControllerTest
{
    @Test
    public void shutdownReturnsAcceptedResponse() throws Exception
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ApplicationShutdownService shutdownService = new ApplicationShutdownService(applicationContext, null);
        SystemController controller = new SystemController(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                shutdownService,
                null,
                null
        );

        ResponseEntity<Map<String, Object>> response = controller.shutdown();

        assertEquals(202, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("accepted")));
        assertEquals("Shutdown requested", response.getBody().get("message"));
    }
}
