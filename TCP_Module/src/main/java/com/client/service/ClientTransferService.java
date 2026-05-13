package com.client.service;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.crypto.AesGcmChunk;
import com.common.protocol.file.*;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.common.service.PushNotificationService;
import com.crypto.CryptoSupport;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
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

    private final Map<String, CompletableFuture<SelectedReceiverDevice>> deviceSelectionFutures = new ConcurrentHashMap<>();//服务器确认目标设备是否存在，并返回目标设备公钥。deviceSelectionFutures关联等待结果
    private final Map<String, OutboundTransferContext>  outboundTransferContexts = new ConcurrentHashMap<>();//保存正在发送的任务的上下文，AES密钥，等待接收方接收的Future，每个块的ACK Future
    private final Map<String, InboundTransferContext> inboundTransferContexts = new ConcurrentHashMap<>();//保存正在接收的任务上下文，AES密钥，输出文件流，已收到但暂时不能写入的块
    private final Map<String, IncomingTransferRequestPacket> pendingIncomingTransferRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<OnlineUserSearchResultPacket>> onlineUserSearchFutures = new ConcurrentHashMap<>();


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

    public OnlineUserSearchResultPacket searchOnlineUser(String accountId)
    {
        if(!clientConnectionManager.isAuthenticated())
        {
            throw new IllegalStateException("Client is not authenticated");
        }
        if(accountId==null || accountId.isBlank())
        {
            throw new IllegalArgumentException("accountId is required");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<OnlineUserSearchResultPacket> future = new CompletableFuture<>();
        onlineUserSearchFutures.put(requestId, future);
        try
        {
            clientConnectionManager.send(new OnlineUserSearchRequestPacket(accountId, requestId));
            return future.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
        }
        catch(Exception e)
        {
            throw new IllegalStateException("Online user search failed: "+e.getMessage(), e);
        }
        finally
        {
            onlineUserSearchFutures.remove(requestId);
        }
    }

    public void handleOnlineUserSearchResult(OnlineUserSearchResultPacket packet)
    {
        CompletableFuture<OnlineUserSearchResultPacket> future=onlineUserSearchFutures.remove(packet.getRequestId());
        if(future==null)
        {
            return;
        }
        future.complete(packet);
    }

    //发送文件的入口方法；创建一个传输任务，然后把真正发送逻辑交给后台线程执行
    public String sendFile(Path filePath, String targetAccountId)//返回taskId
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
                    targetAccountId,
                    fileSize,
                    totalBlocks,
                    Instant.now() //创建时间
            );
            task.updateStatus(TransferStatus.WAITING_FOR_TARGET, "Waiting for receiver device selection");//更新任务状态
            transferTaskRegistry.register(task);//注册任务
            pushNotificationService.publish("transfer-created", task);//推送任务创建事件
            executorService.submit(()->executeSend(task, filePath, targetAccountId));//真正的发送交给后台线程处理，（异步任务）
            return taskId;
        }
        catch (IOException e)
        {
            log.info(e.getStackTrace().toString());
            throw new IllegalStateException("Unable to read file metadata ", e);
        }
    }

    //处理设备选择结果
    public void handleDeviceSelection(DeviceSelectionPacket packet)
    {
        CompletableFuture<SelectedReceiverDevice> future=deviceSelectionFutures.remove(packet.getTransferId());
        if(future==null)
        {
            return;
        }
        if(packet.isConfirmed())//设备选择确认
        {
            future.complete(new SelectedReceiverDevice(packet.getSelectedDeviceId(), packet.getMessage()));
        }
        else
        {
            RuntimeException e=packet.getMessage() != null && packet.getMessage().startsWith("Transfer canceled")? new TransferCanceledException(packet.getMessage(), packet.getSelectedDeviceId()): new IllegalStateException(packet.getMessage());
            future.completeExceptionally(e);
        }
    }

    public void handleFileAccept(FileAcceptPacket packet)
    {
        OutboundTransferContext context = outboundTransferContexts.get(packet.getTransferId());
        if (context != null) {
            context.acceptFuture.complete(packet);
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

    //接收端收到文件发送请求时的处理函数
    public void handleIncomingOffer(FileOfferPacket packet) throws GeneralSecurityException, IOException
    {
        SecretKey secretKey=cryptoSupport.decryptAESKey(packet.getEncryptedSessionKey());//先解密AES密钥
        Path receiveDir= Paths.get(transferProperties.getReceiveDir());
        Files.createDirectories(receiveDir);
        Path outputPath=uniqueReceivePath(receiveDir, packet.getFileName(),packet.getTransferId());//决定文件保存在哪里，并做防重名处理

        //创建接收任务
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
        transferTaskRegistry.register(task);//注册任务

        //打开输出文件流
        OutputStream outputStream=Files.newOutputStream(
          outputPath,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        );

        //创建接收上下文
        inboundTransferContexts.put(packet.getTransferId(),new InboundTransferContext(secretKey, outputStream, task));

        //推送新文件提示
        pushNotificationService.publish("incoming-file-offer",
                Map.of(
                        "transferId", packet.getTransferId(),
                        "fileName", packet.getFileName(),
                        "path", outputPath.toString()
                ));
        clientConnectionManager.send(new FileAcceptPacket(true, "Auto accepted", packet.getTransferId()));//返回接收信息
    }

    //接收方设备主动拒绝传输请求
    public void rejectIncomingTransfer(String transferId)
    {
        IncomingTransferRequestPacket request=pendingIncomingTransferRequests.get(transferId);
        if(request==null)
        {
            throw new IllegalStateException("Incoming transfer request not found: "+transferId);
        }
        clientConnectionManager.send(new ReceiverDeviceSelectionPacket(transferId, false, "Rejected by receiver device"));
    }

    //处理接收到的文件数据块
    public void handleIncommingBlock(FileBlockPacket packet) throws GeneralSecurityException, IOException
    {
        InboundTransferContext context = inboundTransferContexts.get(packet.getTransferId());//根据transferId找到上下文
        if(context==null)//接收方并不知道本次传输
        {
            clientConnectionManager.send(new AckPacket(packet.getBlockId(),false,packet.getTransferId()));
            return;
        }

        //解密数据块
        byte[] plain=cryptoSupport.decryptChunk(packet.getNonce(),packet.getCiphertext(),packet.getTag(),context.secretKey);
        boolean complete;//文件接收完成的标志
        synchronized (context)  //加锁，互斥修改任务上下文变量，避免多个文件块同时修改上下文和写文件
        {
            complete=context.acceptBlock(packet.getBlockId(), plain);
        }

        //回复ACK
        clientConnectionManager.send(new AckPacket(packet.getBlockId(),true,packet.getTransferId()));
        pushNotificationService.publish("transfer-progress", context.transferTask);
        persistTask(context.transferTask);

        if(complete)//判断文件是否接收完成
        {
            inboundTransferContexts.remove(packet.getTransferId());
            pushNotificationService.publish("transfer-complete", context.transferTask);
            persistTask(context.transferTask);
        }
    }

    //获取当前客户端的连接状态
    public Map<String, Object> clientStatus()
    {
        return clientConnectionManager.currentStatus();
    }

    //真正执行发送文件的函数
    //目前是发送一块等待一块的ACK结果---2026-04-27；可以改成发送窗口的模式
    private void executeSend(TransferTask task, Path filePath, String targetAccountId)
    {
        OutboundTransferContext context = null;
        try
        {
            //准备接收方的公钥
            CompletableFuture<SelectedReceiverDevice> recipientFuture=new CompletableFuture<>();
            deviceSelectionFutures.put(task.getTransferId(), recipientFuture);

            //发送设备选择请求
            clientConnectionManager.send(new TransferRequestPacket(
                    task.getTransferId(),
                    targetAccountId,
                    task.getFileName(),
                    task.getTotalBytes(),
                    task.getTotalBlocks()
                    )
            );//发送后，当前线程等待结果

            SelectedReceiverDevice selectedReceiverDevice=recipientFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);//会阻塞当前线程直到服务器返回目标设备信息，或者等待超时
            task.updatePeerDeviceId(selectedReceiverDevice.getDeviceId());
            persistTask(task);
            String recipientPublicKey=selectedReceiverDevice.getPublicKey();
            SecretKey secretKey=cryptoSupport.generateAESKey();//生成本次传输使用的AES密钥，本次传输文件使用的对称加密密钥
            String encryptedSessionKey=cryptoSupport.encryptAESKeyForReceiver(secretKey, recipientPublicKey);//用接收方的公钥加密这个AES密钥

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
                            clientConnectionManager.getLocalPublicKey(),
                            task.getTotalBlocks(),
                            task.getTransferId()
                    )
            );

            //等待接收方接受
            FileAcceptPacket fileAcceptPacket=context.acceptFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(!fileAcceptPacket.isAccept())//判断是否被拒收
            {
                task.updateStatus(TransferStatus.REJECTED, fileAcceptPacket.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-rejected", task);
                outboundTransferContexts.remove(task.getTransferId());
                return;
            }

            //同意接收
            //更新任务状体
            task.updateStatus(TransferStatus.TRANSFERRING, "Sending blocks");
            persistTask(task);

            //读取本地文件
            try(InputStream inputStream=Files.newInputStream(filePath))
            {
                byte[] buffer=new byte[transferProperties.getChunkSizeBytes()];//分块读取
                int blockId=0;
                int length;
                long sentBytes=0;

                while((length=inputStream.read(buffer))!=-1)//只要还能读到文件就继续读
                {
                    byte[] plain=new byte[length];
                    System.arraycopy(buffer,0,plain,0,length);//拷贝当前块
                    AesGcmChunk encryptedChunk=cryptoSupport.encryptChunk(plain,secretKey);//加密当前块
                    CompletableFuture<Boolean> ackFuture=new CompletableFuture<>();//当前块的ACK等待对像
                    context.ackFutures.put(blockId, ackFuture);
                    //发送
                    clientConnectionManager.send(
                            new FileBlockPacket(
                                    blockId,
                                    encryptedChunk.ciphertext(),
                                    encryptedChunk.nonce(),
                                    encryptedChunk.tag(),
                                    task.getTransferId()
                            )
                    );

                    //等待该块的ACK结果
                    Boolean success=ackFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
                    if(!Boolean.TRUE.equals(success))//判断ACK是否失败
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

            task.updateStatus(TransferStatus.COMPLETED, "File sent");//发送完成，更新任务状态
            persistTask(task);
            pushNotificationService.publish("transfer-complete", task);//推送完成的消息
        }
        catch(Exception e)
        {
            log.info("TransferStatus: FAILED, "+e.getMessage());
            Throwable cause=e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            if(cause instanceof TransferCanceledException)
            {
                task.updatePeerDeviceId(((TransferCanceledException)cause).getDeviceId());
                task.updateStatus(TransferStatus.CANCELED, cause.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-cancelled", task);
            }
            else
            {
                task.updateStatus(TransferStatus.FAILED, e.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-failed", task);
            }
        }
        finally     //清理临时状态
        {
            outboundTransferContexts.remove(task.getTransferId());
            deviceSelectionFutures.remove(task.getTransferId());
            if(context!=null)
            {
                context.ackFutures.clear();
            }
        }

    }

    //创建独一文件名，避免重名
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

    public void handleIncomingTransferRequest(IncomingTransferRequestPacket packet)
    {
        pendingIncomingTransferRequests.put(packet.getTransferId(), packet);
        pushNotificationService.publish("incoming-transfer-request", Map.of(
                "transferId", packet.getTransferId(),
                "senderDeviceId", packet.getSenderDeviceId(),
                "targetAccountId", packet.getTargetAccountId(),
                "fileName", packet.getFileName(),
                "fileSize", packet.getFileSize(),
                "totalBlocks", packet.getTotalBlocks()
        ));
    }

    public void handleReceiverDeviceSelection(ReceiverDeviceSelectionPacket packet)
    {
        if (packet.isAccepted()) {
            pendingIncomingTransferRequests.remove(packet.getTransferId());
            pushNotificationService.publish("incoming-transfer-selected", Map.of(
                    "transferId", packet.getTransferId(),
                    "message", packet.getMessage()
            ));
            return;
        }
        pendingIncomingTransferRequests.remove(packet.getTransferId());
        pushNotificationService.publish("incoming-transfer-selection-failed", Map.of(
                "transferId", packet.getTransferId(),
                "message", packet.getMessage()
        ));
    }

    public void acceptIncomingTransfer(String transferId)
    {
        IncomingTransferRequestPacket request = pendingIncomingTransferRequests.get(transferId);
        if (request == null) {
            throw new IllegalArgumentException("Incoming transfer request not found: " + transferId);
        }
        clientConnectionManager.send(new ReceiverDeviceSelectionPacket(transferId, true, "Accepted by receiver device"));
    }

    public Map<String, IncomingTransferRequestPacket> pendingIncomingTransferRequests()
    {
        return Map.copyOf(pendingIncomingTransferRequests);
    }


    //临时数据结构，承接服务端返回的最终接收设备
    private class SelectedReceiverDevice
    {
        private String deviceId;
        private String publicKey;

        public SelectedReceiverDevice(String deviceId, String publicKey) {
            this.deviceId = deviceId;
            this.publicKey = publicKey;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        @Override
        public String toString() {
            return "SelectedReceiverDevice{" +
                    "deviceId='" + deviceId + '\'' +
                    ", publicKey='" + publicKey + '\'' +
                    '}';
        }
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

    //用来区分普通失败和接收方拒绝接收的情况
    private static final class TransferCanceledException extends RuntimeException
    {
        private final String deviceId;

        private TransferCanceledException(String message, String deviceId)
        {
            super(message);
            this.deviceId=deviceId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        @Override
        public String toString() {
            return "TransferCanceledException{" +
                    "deviceId='" + deviceId + '\'' +
                    '}';
        }
    }
}
