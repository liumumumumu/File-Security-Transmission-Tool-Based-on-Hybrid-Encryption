package com.client.controller;

import com.client.service.ClientTransferService;
import com.client.service.LocalTransferHistoryService;
import com.client.service.TransferTaskRegistry;
import com.common.config.ClientProperties;
import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.service.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ReceiveControllerTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void retransmitErrorsReturnReadableBadRequest()
    {
        TransferTaskRegistry registry = transferTaskRegistry();
        ReceiveController controller = new ReceiveController(service(registry), registry);

        ResponseEntity<Map<String, Object>> response = controller.requestRetransmission(Map.of("transferId", "missing-transfer"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("success"));
        assertEquals("Transfer task not found: missing-transfer", response.getBody().get("message"));
    }

    private ClientTransferService service()
    {
        return service(transferTaskRegistry());
    }

    private ClientTransferService service(TransferTaskRegistry registry)
    {
        return new ClientTransferService(
                null,
                new ClientProperties(),
                null,
                nodeProperties(),
                new PushNotificationService(),
                transferProperties(),
                registry
        );
    }

    private NodeProperties nodeProperties()
    {
        NodeProperties properties = new NodeProperties();
        properties.setDeviceId("device-1");
        return properties;
    }

    private TransferProperties transferProperties()
    {
        TransferProperties properties = new TransferProperties();
        properties.setChunkSizeBytes(1024);
        properties.setSendWindowSize(4);
        properties.setReceiveDir(temporaryFolder.getRoot().toPath().resolve("receive").toString());
        return properties;
    }

    private TransferTaskRegistry transferTaskRegistry()
    {
        LocalStorageProperties localStorageProperties = new LocalStorageProperties();
        localStorageProperties.setTransferHistoryPath(temporaryFolder.getRoot().toPath().resolve("history.json").toString());
        return new TransferTaskRegistry(new LocalTransferHistoryService(new ObjectMapper().findAndRegisterModules(), localStorageProperties));
    }
}
