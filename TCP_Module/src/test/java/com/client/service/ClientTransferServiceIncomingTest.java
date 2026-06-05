package com.client.service;

import com.common.config.ClientProperties;
import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.file.TransferCancelPacket;
import com.common.service.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ClientTransferServiceIncomingTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pendingIncomingRequestsKeepAllUnhandledRequests() throws Exception
    {
        ClientTransferService service = service();

        service.handleIncomingTransferRequest(new IncomingTransferRequestPacket("transfer-1", "sender-a", "target", "a.txt", 100L, 1));
        Thread.sleep(5L);
        service.handleIncomingTransferRequest(new IncomingTransferRequestPacket("transfer-2", "sender-b", "target", "b.txt", 200L, 2));

        List<ClientTransferService.PendingIncomingTransferRequest> requests = service.pendingIncomingTransferRequestsDetailed();

        assertEquals(2, requests.size());
        assertEquals("transfer-2", requests.get(0).packet().getTransferId());
        assertEquals("transfer-1", requests.get(1).packet().getTransferId());
    }

    @Test
    public void transferCancelRemovesUnhandledIncomingRequest()
    {
        ClientTransferService service = service();
        service.handleIncomingTransferRequest(new IncomingTransferRequestPacket("transfer-1", "sender-a", "target", "a.txt", 100L, 1));

        service.handleTransferCancel(new TransferCancelPacket("transfer-1", "sender canceled"));

        assertEquals(0, service.pendingIncomingTransferRequestsDetailed().size());
    }

    private ClientTransferService service()
    {
        return new ClientTransferService(
                null,
                new ClientProperties(),
                null,
                nodeProperties(),
                new PushNotificationService(),
                transferProperties(),
                transferTaskRegistry()
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
        return new TransferTaskRegistry(new LocalTransferHistoryService(new ObjectMapper(), localStorageProperties));
    }
}
