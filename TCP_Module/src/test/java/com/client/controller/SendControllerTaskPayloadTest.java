package com.client.controller;

import com.client.service.LocalTransferHistoryService;
import com.client.service.TransferTaskRegistry;
import com.common.config.LocalStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SendControllerTaskPayloadTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void listTasksIncludesProgressAndSpeedFields()
    {
        TransferTaskRegistry registry = transferTaskRegistry();
        TransferTask task = new TransferTask(
                UUID.randomUUID().toString(),
                "transfer-1",
                TransferDirection.RECEIVE,
                "receive.bin",
                temporaryFolder.getRoot().toPath().resolve("receive.bin").toString(),
                "peer-device",
                2L * 1024L * 1024L,
                2,
                Instant.now()
        );
        task.updateStatus(TransferStatus.TRANSFERRING, "Receiving blocks");
        task.updateProgress(1024L * 1024L, 1);
        registry.register(task);

        SendController controller = new SendController(null, null, registry);
        ResponseEntity<List<Map<String, Object>>> response = controller.listTasks();
        Map<String, Object> payload = response.getBody().get(0);

        assertEquals("transfer-1", payload.get("transferId"));
        assertEquals(1024L * 1024L, payload.get("transferredBytes"));
        assertEquals(2L * 1024L * 1024L, payload.get("totalBytes"));
        assertEquals(1, payload.get("transferredBlocks"));
        assertEquals(2, payload.get("totalBlocks"));
        assertNotNull(payload.get("transferStartedAt"));
        assertTrue(((Number) payload.get("speedMegabytesPerSecond")).doubleValue() > 0D);
        assertTrue(String.valueOf(payload.get("speedText")).endsWith("MB/s"));
    }

    private TransferTaskRegistry transferTaskRegistry()
    {
        LocalStorageProperties localStorageProperties = new LocalStorageProperties();
        localStorageProperties.setTransferHistoryPath(temporaryFolder.getRoot().toPath().resolve("history.json").toString());
        return new TransferTaskRegistry(new LocalTransferHistoryService(new ObjectMapper().findAndRegisterModules(), localStorageProperties));
    }
}
