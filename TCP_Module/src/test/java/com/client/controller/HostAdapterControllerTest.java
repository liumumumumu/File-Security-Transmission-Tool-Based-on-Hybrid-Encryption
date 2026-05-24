package com.client.controller;

import org.junit.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HostAdapterControllerTest
{
    @Test
    public void shutdownPageReturnsHtml()
    {
        HostAdapterController controller = new HostAdapterController();

        ResponseEntity<String> response = controller.shutdownPage();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("<!doctype html>"));
        assertTrue(response.getBody().contains("Shutdown Adapter"));
        assertTrue(response.getBody().contains("/api/system/shutdown"));
    }
}
