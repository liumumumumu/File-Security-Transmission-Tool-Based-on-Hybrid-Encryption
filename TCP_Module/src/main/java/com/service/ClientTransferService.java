package com.service;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.protocol.file.AckPacket;
import com.common.protocol.file.DeviceSelectionPacket;
import com.common.protocol.file.FileAcceptPacket;
import com.common.protocol.file.FileOfferPacket;
import com.crypto.CryptoSupport;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class ClientTransferService
{
    private final ClientConnectionManager clientConnectionManager;
    private final CryptoSupport cryptoSupport;
    private final TransferProperties transferProperties;
    private final ClientProperties clientProperties;
    private final NodeProperties nodeProperties;
    private final PushNotificationService pushNotificationService;
    private final TransferTaskRegistry transferTaskRegistry;

    private final ExecutorService executorService= Executors.newCachedThreadPool();
    private final Map<String, CompletableFuture<String>> deviceSelectionFutures = new ConcurrentHashMap<>();
    private final Map<String, OutboundTransferContext>  outboundTransferContexts = new ConcurrentHashMap<>();
    private final Map<String, InboundTransferContext> inboundTransferContexts = new ConcurrentHashMap<>();


    public ClientTransferService(ClientConnectionManager clientConnectionManager, ClientProperties clientProperties, CryptoSupport cryptoSupport, NodeProperties nodeProperties, PushNotificationService pushNotificationService, TransferProperties transferProperties, TransferTaskRegistry transferTaskRegistry) {
        this.clientConnectionManager = clientConnectionManager;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.nodeProperties = nodeProperties;
        this.pushNotificationService = pushNotificationService;
        this.transferProperties = transferProperties;
        this.transferTaskRegistry = transferTaskRegistry;
    }

    public void autoConectIfConfigured() throws Exception
    {
        if(nodeProperties.isAutoConnect())
        {
            clientConnectionManager.connectAndAuthenticate(clientProperties.getServerHost(), clientProperties.getServerPort()).get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        }
    }

    public String sendFile(Path filePath, String targetDeviceId)
    {
        if(!clientConnectionManager.isAuthenticated())
        {
            throw new IllegalStateException("Client is not authenticated");
        }

        if(!Files.exists(filePath) || Files.isRegularFile(filePath))
        {
            throw new IllegalStateException("File does not exist: "+filePath);
        }

        String transferId= UUID.randomUUID().toString();
        String taskId= UUID.randomUUID().toString();

        try
        {
            long fileSize = Files.size(filePath);
            int totalBlocks=(int)((fileSize+transferProperties.getChunkSizeBytes()-1)/transferProperties.getChunkSizeBytes());

            TransferTask task=new TransferTask(
                    taskId,
                    transferId,
                    TransferDirection.SEND,
                    filePath.getFileName().toString(),
                    filePath.toAbsolutePath().toString(),
                    targetDeviceId,
                    fileSize,
                    totalBlocks,
                    Instant.now()
            );
            task.updateStatus(TransferStatus.WAITING_FOR_TARGET, "Resolving target device");
            transferTaskRegistry.register(task);
            pushNotificationService.publish("transfer-created", task);
            executorService.submit(()->executeSend(task, filePath, targetDeviceId));
            return taskId;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to read file metadata ", e);
            log.info(e.getStackTrace().toString());
        }
    }

    public void handleDeviceSelection(DeviceSelectionPacket packet)
    {
        CompletableFuture<String> future=deviceSelectionFutures.remove(packet.getTransferId());
        if(future==null)
        {
            return;
        }
        if(packet.isConfirmed())
        {
            future.complete(packet.getMessage());
        }
        else
        {
            future.completeExceptionally(new IllegalStateException(packet.getMessage()));
        }
    }

    public void handleAck(AckPacket packet)
    {
        OutboundTransferContext context = outboundTransferContexts.get(packet.getTransferId());
        if(context==null)
        {
            return;
        }
        CompletableFuture<Boolean> future=context.ackFutures.remove(packet.getBlockedId());
        if(future!=null)
        {
            future.complete(packet.isSuccess());
        }
    }

    public void handleIncomingOffer(FileOfferPacket packet) throws GeneralSecurityException, IOException
    {
        SecretKey secretKey=cryptoSupport.decryptAesKey(packet.getEncryptedSessionKey());
        Path receiveDir= Paths.get(transferProperties.getReceiveDir());
        Files.createDirectories(receiveDir);
        Path outputPath=uniqueReceivePath(receiveDir, packet.getFileName(),packet.getTransferId());

        TransferTask task=new TransferTask(
                UUID.randomUUID().toString(),
                packet.getTransferId(),
                TransferDirection.RECEIVE,
                packet.getFileName(),
                outputPath.toString(),
                packet.getSenderPublicKey(),
                packet.getFileSize(),
                packet.getTotalBlocks(),
                Instant.now()
        );
        task.updateStatus(TransferStatus.TRANSFERRING, "Receiving file");
        transferTaskRegistry.register(task);
        OutputStream outputStream=Files.newOutputStream(
          outputPath,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        );
        inboundTransferContexts.put(packet.getTransferId(),new InboundTransferContext(secretKey, outputStream, task));
        pushNotificationService.publish("incoming-file-offer",
                Map.of(
                        "transferId", packet.getTransferId(),
                        "fileName", packet.getFileName(),
                        "path", outputPath.toString()
                ));
        clientConnectionManager.send(new FileAcceptPacket(true, "Auto accepted", packet.getTransferId()));
    }

    //发送端传输上下文对象，在向外发送文件的过程中，把这次需要用到的状态集中保存起来
    private static final class OutboundTransferContext
    {
        private final SecretKey secretKey;//AES密钥
        private final TransferTask transferTask;//当前传输任务本身
        private final String recipientPublicKey;//接收方的公钥
        private final CompletableFuture<FileAcceptPacket> acceptFuture=new CompletableFuture<>();//等待接收方“接受文件”的异步结果
        private final Map<Integer, CompletableFuture<Boolean>> ackFutures=new ConcurrentHashMap<>();//每个文件块的ACK等待结果Map

        private OutboundTransferContext(SecretKey secretKey, TransferTask transferTask, String recipientPublicKey)
        {
            this.secretKey=secretKey;
            this.transferTask=transferTask;
            this.recipientPublicKey=recipientPublicKey;
        }
    }

    //接收文件的时候，用这个来保存接收过程中的临时状态，如AES密钥，输出文件流，传输任务，收到的块，哪些块暂时不能写入，当前是第几块
    private static final class InboundTransferContext
    {
        private final SecretKey secretKey;
        private final OutputStream outputStream;
        private final TransferTask transferTask;//本次接收任务的状态对象
        private final Map<Integer, byte[]> pendingBlocks=new ConcurrentHashMap<>();//暂存已经收到，但还不能马上写入文件的块
        private final Set<Integer> receivedBlockIds=new HashSet<>();//已经接收过的块编号
        private long receivedBytes;//已经写入文件的字节数
        private int receivedBlocks;//已经写入的块数
        private int nextWriteBlockId;//下一个应该写入的块编号

        private InboundTransferContext(SecretKey secretKey, OutputStream outputStream, TransferTask transferTask)
        {
            this.secretKey=secretKey;
            this.outputStream=outputStream;
            this.transferTask=transferTask;
        }

        //接受一个已经解密好的文件块
        private boolean acceptBlock(int blockId, byte[] plain)throws IOException
        {
            //判断该块是否已经接收过了
            if(receivedBlockIds.contains(blockId))
            {
                return isCompleted();
            }

            receivedBlockIds.add(blockId);
            pendingBlocks.put(blockId, plain); //先放入缓存再检查nextWriteBlockId有没有联系的块可以写
            flushContiguousBlocks();    //按顺序写入文件
            transferTask.updateProgress(receivedBytes, receivedBlocks);
            transferTask.updateStatus(TransferTask.TRANSFERRING, "Receiving blocks");

            //判断文件是否接收完成
            if(isCompleted())
            {
                outputStream.flush();
                outputStream.close();
                transferTask.updateStatus(TransferTask.COMPILED, "File received");
                return true;
            }
            return false;
        }

        private void flushContiguousBlocks() throws IOException
        {
            while(true)
            {
                //从nextWriteBlockId开始循环取块
                byte[] chunk=pendingBlocks.remove(nextWriteBlockId);

                //如果收到的块号是不连续的那么这里取出来的块就是0
                if(chunk==null)
                {
                    return;
                }
                outputStream.write(chunk);//写入文件
                receivedBytes+=chunk.length;
                receivedBlocks++;
                nextWriteBlockId++;
            }
        }

        private boolean isCompleted()
        {
            //收到块数>=文件总块数说明取完了
            return receivedBlocks >= transferTask.getTotalBlocks();
        }

    }
}
