package com.client.service;

import com.common.config.ClientProperties;
import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.protocol.file.RetransmitRequestPacket;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.file.TransferCancelPacket;
import com.common.service.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.client.transport.TransportMode;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

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

    @Test
    public void rejectIncomingTransferCanRejectActiveReceiveTask()
    {
        TransferTaskRegistry registry = transferTaskRegistry();
        ClientTransferService service = service(registry);
        TransferTask task = new TransferTask(
                UUID.randomUUID().toString(),
                "transfer-active",
                TransferDirection.RECEIVE,
                "active.txt",
                temporaryFolder.getRoot().toPath().resolve("active.txt").toString(),
                "sender-a",
                100L,
                1,
                Instant.now()
        );
        task.updateStatus(TransferStatus.TRANSFERRING, "Receiving file");
        registry.register(task);

        service.rejectIncomingTransfer("transfer-active");

        assertEquals(TransferStatus.REJECTED, task.getStatus());
    }

    @Test
    public void completedSendTaskCanReceivePendingRetransmissionRequest() throws Exception
    {
        TransferTaskRegistry registry = transferTaskRegistry();
        ClientTransferService service = service(registry);
        java.io.File source = temporaryFolder.newFile("sent.txt");
        TransferTask task = new TransferTask(
                UUID.randomUUID().toString(),
                "transfer-complete",
                TransferDirection.SEND,
                "sent.txt",
                source.toPath().toString(),
                "receiver-a",
                100L,
                10,
                Instant.now(),
                TransportMode.SERVER_RELAY
        );
        task.updateStatus(TransferStatus.COMPLETED, "File sent");
        registry.register(task);
        putOutboundContext(service, task);

        service.handleRetransmitRequest(new RetransmitRequestPacket("transfer-complete", 3, "missing block"));

        assertEquals(1, service.pendingRetransmitRequests().size());
        assertEquals(TransferStatus.WAITING_FOR_ACCEPT, task.getStatus());
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

    @SuppressWarnings("unchecked")
    private void putOutboundContext(ClientTransferService service, TransferTask task) throws Exception
    {
        Class<?> contextClass = Class.forName("com.client.service.ClientTransferService$OutboundTransferContext");
        java.lang.reflect.Constructor<?> constructor = contextClass.getDeclaredConstructor(SecretKey.class, TransferTask.class, String.class);
        constructor.setAccessible(true);
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(128);
        Object context = constructor.newInstance(keyGenerator.generateKey(), task, "receiver-public-key");

        java.lang.reflect.Field field = ClientTransferService.class.getDeclaredField("outboundTransferContexts");
        field.setAccessible(true);
        ((Map<String, Object>) field.get(service)).put(task.getTransferId(), context);
    }
}
