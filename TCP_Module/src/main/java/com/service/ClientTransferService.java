package com.service;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.crypto.AesGcmChunk;
import com.common.protocol.file.*;
import com.crypto.CryptoSupport;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import io.netty.util.concurrent.CompleteFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;


/**
 * Author: LQH
 * Date: 2026-04-26
 * Purpose: 客户端文件传输业务类，收发文件流程的调度中心
 * 负责：
 * 1.连接服务器并认证
 * 2.发起文件发送任务
 * 3.处理目标设备选择结果
 * 4.发送文件offer
 * 5.等待接收方接收
 * 6.分块加密发送文件
 * 7.等待每个文件块的ACK
 * 8.接收发来的文件
 * 9.解密并按顺序写入本地文件
 * 10.更新传输任务状态，并推送通知
 *
 * */

@Service
@Slf4j
public class ClientTransferService
{
    private final ClientConnectionManager clientConnectionManager;//负责网络连接，认证状态，发送packet
    private final CryptoSupport cryptoSupport;//提供加密，解密，生成AES密钥，RSA加密AES密钥，AES-GCM加密解密文件块

    //读取配置
    private final TransferProperties transferProperties;//分块大小，接收目录
    private final ClientProperties clientProperties;//服务器地址，超时时间，
    private final NodeProperties nodeProperties;//是否启动自动连接
    private final PushNotificationService pushNotificationService;//发布事件
    private final TransferTaskRegistry transferTaskRegistry;//保存和管理传输任务状态

    //创建线程池
    private final ExecutorService executorService= Executors.newCachedThreadPool();//把耗时的任务放到后台线程执行，避免阻塞调用方

    private final Map<String, CompletableFuture<String>> deviceSelectionFutures = new ConcurrentHashMap<>();//服务器确认目标设备是否存在，并返回目标设备公钥。deviceSelectionFutures关联等待结果
    private final Map<String, OutboundTransferContext>  outboundTransferContexts = new ConcurrentHashMap<>();//保存正在发送的任务的上下文，AES密钥，等待接收方接收的Future，每个块的ACK Future
    private final Map<String, InboundTransferContext> inboundTransferContexts = new ConcurrentHashMap<>();//保存正在接收的任务上下文，AES密钥，输出文件流，已收到但暂时不能写入的块


    public ClientTransferService(ClientConnectionManager clientConnectionManager, ClientProperties clientProperties, CryptoSupport cryptoSupport, NodeProperties nodeProperties, PushNotificationService pushNotificationService, TransferProperties transferProperties, TransferTaskRegistry transferTaskRegistry) {
        this.clientConnectionManager = clientConnectionManager;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.nodeProperties = nodeProperties;
        this.pushNotificationService = pushNotificationService;
        this.transferProperties = transferProperties;
        this.transferTaskRegistry = transferTaskRegistry;
    }

    //如果用户开启了自动连接，就在客户端启动时自动连接并认证服务器
    public void autoConnectIfConfigured() throws Exception
    {
        if(nodeProperties.isAutoConnect())
        {
            //连接服务器并认证
            clientConnectionManager.connectAndAuthenticate(clientProperties.getServerHost(), clientProperties.getServerPort()).get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        }
    }

    //发送文件的入口方法；创建一个传输任务，然后把真正发送逻辑交给后台线程执行
    public String sendFile(Path filePath, String targetDeviceId)//返回taskId
    {
        //检查是否认证
        if(!clientConnectionManager.isAuthenticated())
        {
            throw new IllegalStateException("Client is not authenticated");
        }

        //检查文件路径
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }

        //检查文件是否可以用二进制的形式打开
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }


        String transferId= UUID.randomUUID().toString();
        String taskId= UUID.randomUUID().toString();

        try
        {
            long fileSize = Files.size(filePath);
            int totalBlocks=(int)((fileSize+transferProperties.getChunkSizeBytes()-1)/transferProperties.getChunkSizeBytes());//总块数

            //创建传输任务对象
            TransferTask task=new TransferTask(
                    taskId,
                    transferId,
                    TransferDirection.SEND,
                    filePath.getFileName().toString(),
                    filePath.toAbsolutePath().toString(),
                    targetDeviceId,
                    fileSize,
                    totalBlocks,
                    Instant.now() //创建时间
            );
            task.updateStatus(TransferStatus.WAITING_FOR_TARGET, "Resolving target device");//更新任务状态
            transferTaskRegistry.register(task);//注册任务
            pushNotificationService.publish("transfer-created", task);//推送任务创建事件
            executorService.submit(()->executeSend(task, filePath, targetDeviceId));//真正的发送交给后台线程处理，（异步任务）
            return taskId;
        }
        catch (IOException e)
        {
            log.info(e.getStackTrace().toString());
            throw new IllegalStateException("Unable to read file metadata ", e);
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

    public void handleIncommingBlock(FileBlockPacket packet) throws GeneralSecurityException, IOException
    {
        InboundTransferContext context = inboundTransferContexts.get(packet.getTransferId());
        if(context==null)
        {
            clientConnectionManager.send(new AckPacket(packet.getBlockId(),false,packet.getTransferId()));
            return;
        }

        byte[] plain=cryptoSupport.decryptChunk(packet.getNonce(),packet.getCiphertext(),packet.getTag(),context.secretKey);
        boolean complete;
        synchronized (context)
        {
            complete=context.acceptBlock(packet.getBlockId(), plain);
        }

        clientConnectionManager.send(new AckPacket(packet.getBlockId(),true,packet.getTransferId()));
        pushNotificationService.publish("transfer-progress", context.transferTask);
        persistTask(context.transferTask);

        if(complete)
        {
            inboundTransferContexts.remove(packet.getTransferId());
            pushNotificationService.publish("transfer-complete", context.transferTask);
            persistTask(context.transferTask);
        }
    }

    public Map<String, Object> clientStatus()
    {
        return clientConnectionManager.currentStatus();
    }

    private void executeSend(TransferTask task, Path filePath, String targetDeviceId)
    {
        OutboundTransferContext context = null;
        try
        {
            //准备接收方的公钥
            CompletableFuture<String> recipientKeyFuture=new CompletableFuture<>();
            deviceSelectionFutures.put(task.getTransferId(), recipientKeyFuture);

            //发送设备选择请求
            clientConnectionManager.send(
                    new DeviceSelectionPacket(
                            false,
                            "",
                            targetDeviceId,
                            task.getTransferId()
                    )
            );//发送后，当前线程等待结果

            String recipientPublicKey=recipientKeyFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);//会阻塞当前线程直到服务器返回目标设备信息，或者等待超时
            SecretKey secretKey=cryptoSupport.generateAesKey();//生成本次传输使用的AES密钥，本次传输文件使用的对称加密密钥
            String encryptedSessionKey=cryptoSupport.encryptKeyForReceiver(secretKey, recipientPublicKey);//用接收方的公钥加密这个AES密钥

            //创建本次发送任务的上下文
            context=new OutboundTransferContext(secretKey, task,recipientPublicKey);
            outboundTransferContexts.put(task.getTransferId(),context);//本次发送过程中的临时状态

            //更新任务状态
            task.updateStatus(TransferStatus.WAITING_FOR_ACCEPT, "Waiting for receiver acceptance");
            persistTask(task);

            //发送本次传输的文件信息
            clientConnectionManager.send(
                    new FileOfferPacket(
                            encryptedSessionKey,
                            task.getFileName(),
                            task.getTotalBytes(),
                            recipientPublicKey,
                            clientConnectionManager.getLocalPublickey(),
                            task.getTotalBlocks(),
                            task.getTransferId()
                    )
            );

            //等待接收方接受
            FileAcceptPacket fileAcceptPacket=context.acceptFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(!fileAcceptPacket.isAccept())
            {
                task.updateStatus(TransferStatus.REJECTED, fileAcceptPacket.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-rejected", task);
                outboundTransferContexts.remove(task.getTransferId());
                return;
            }

            task.updateStatus(TransferStatus.TRANSFERRING, "Sending blocks");
            persistTask(task);

            try(InputStream inputStream=Files.newInputStream(filePath))
            {
                byte[] buffer=new byte[transferProperties.getChunkSizeBytes()];
                int blockId=0;
                int length;
                long sentBytes=0;

                while((length=inputStream.read(buffer))!=-1)
                {
                    byte[] plain=new byte[length];
                    System.arraycopy(buffer,0,plain,0,length);
                    AesGcmChunk encryptedChunk=cryptoSupport.encryptChunk(plain,secretKey);
                    CompletableFuture<Boolean> ackFuture=new CompletableFuture<>();
                    context.ackFutures.put(blockId, ackFuture);
                    clientConnectionManager.send(
                            new FileBlockPacket(
                                    blockId,
                                    encryptedChunk.ciphertext(),
                                    encryptedChunk.nonce(),
                                    encryptedChunk.tag(),
                                    task.getTransferId()
                            )
                    );

                    Boolean success=ackFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
                    if(!Boolean.TRUE.equals(success))
                    {
                        throw new IllegalStateException("ACK failed for block "+blockId);
                    }

                    blockId++;
                    sentBytes+=length;
                    task.updateProgress(sentBytes, blockId);
                    persistTask(task);
                    pushNotificationService.publish("transfer-progress", task);
                }
            }

            task.updateStatus(TransferStatus.COMPLETED, "File sent");
            persistTask(task);
            pushNotificationService.publish("transfer-complete", task);
        }
        catch(Exception e)
        {
            log.info("TransferStatus: FAILED, "+e.getMessage());
            task.updateStatus(TransferStatus.FAILED, e.getMessage());
            persistTask(task);
            pushNotificationService.publish("transfer-failed", task);
        }
        finally
        {
            outboundTransferContexts.remove(task.getTransferId());
            deviceSelectionFutures.remove(task.getTransferId());
            if(context!=null)
            {
                context.ackFutures.clear();
            }
        }

    }

    private Path uniqueReceivePath(Path receiveDir, String fileName, String transferId)
    {
        Path target=receiveDir.resolve(fileName);
        if(!Files.exists(target))
        {
            return target;
        }
        return receiveDir.resolve(transferId+"-"+fileName);
    }

    private void persistTask(TransferTask task)
    {
        transferTaskRegistry.persist();
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
            transferTask.updateStatus(TransferStatus.TRANSFERRING, "Receiving blocks");

            //判断文件是否接收完成
            if(isCompleted())
            {
                outputStream.flush();
                outputStream.close();
                transferTask.updateStatus(TransferStatus.COMPLETED, "File received");
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
