package com.client.service;

import com.client.ClientConnectionManager;
import com.client.direct.qr.ReceiverResponseQr;
import com.client.transport.DirectPeerTransport;
import com.client.transport.PacketTransport;
import com.client.transport.ServerRelayTransport;
import com.client.transport.TransportMode;
import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.crypto.AesGcmChunk;
import com.common.protocol.file.*;
import com.common.protocol.Packet;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.common.service.PushNotificationService;
import com.crypto.CryptoSupport;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import jakarta.annotation.PreDestroy;
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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;


/**
 * Author: LQH
 * Date: 2026-04-26
 * Purpose: 客户端文件传输业务类，收发文件流程的调度中心
 * 负责：
 * 1. 自动连接服务器，并配合 ClientConnectionManager 完成认证
 * 2. 发起文件发送任务，向服务端请求目标账号下的接收设备
 * 3. 处理目标设备选择结果，生成本次传输的 AES 会话密钥，并用接收方公钥加密
 * 4. 发送文件 offer，等待接收方接受或拒绝
 * 5. 分块读取、AES-GCM 加密并发送文件内容
 * 6. 维护发送窗口，等待并处理每个文件块的 ACK
 * 7. 接收文件 offer，解密会话密钥，创建接收任务和接收上下文
 * 8. 接收文件块，完成 AES-GCM 解密认证后先放入接收缓存，并根据缓存压力立即或延迟回复 ACK
 * 9. 后台按块号顺序写入本地文件，避免磁盘写入和进度持久化阻塞 ACK 返回
 * 10. 使用接收缓存高低水位释放延迟 ACK，对发送端形成动态背压，避免接收端内存无限增长
 * 11. 对接收进度事件和任务持久化做节流，减少每块写入带来的额外开销
 * 12. 支持接收请求的接受/拒绝、传输取消、取消确认和本地资源清理
 * 13. 支持断点重传请求、发送方确认/拒绝重传，以及从指定块重新发送
 * 14. 支持在线用户查询，用于联系人和目标账号公钥获取
 * 15. 更新传输任务状态、持久化任务，并推送控制台/UI 通知
 *
 * */

@Service
@Slf4j
public class ClientTransferService
{
    private static final long RECEIVE_PROGRESS_UPDATE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(500);
    private static final long RECEIVE_PENDING_HIGH_WATERMARK_BYTES = 256L * 1024 * 1024;
    private static final long RECEIVE_PENDING_LOW_WATERMARK_BYTES = 128L * 1024 * 1024;
    private static final Duration DIRECT_RETRANSMIT_RETENTION = Duration.ofHours(24);
    private static final long DIRECT_RETRANSMIT_CLEANUP_INTERVAL_MINUTES = 5L;

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
    private final ScheduledExecutorService cleanupExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "direct-retransmit-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, CompletableFuture<SelectedReceiverDevice>> deviceSelectionFutures = new ConcurrentHashMap<>();//服务器确认目标设备是否存在，并返回目标设备公钥。deviceSelectionFutures关联等待结果
    private final Map<String, CompletableFuture<ReceiverDeviceSelectionPacket>> directReceiverSelectionFutures = new ConcurrentHashMap<>();
    private final Map<String, OutboundTransferContext>  outboundTransferContexts = new ConcurrentHashMap<>();//保存正在发送的任务的上下文，AES密钥，等待接收方接收的Future，每个块的ACK Future
    private final Map<String, InboundTransferContext> inboundTransferContexts = new ConcurrentHashMap<>();//保存正在接收的任务上下文，AES密钥，输出文件流，已收到但暂时不能写入的块
    private final Map<String, PendingIncomingTransferRequest> pendingIncomingTransferRequests = new ConcurrentHashMap<>();
    private final Map<String, PacketTransport> transferTransports = new ConcurrentHashMap<>();
    private final Map<String, TransportMode> transferModes = new ConcurrentHashMap<>();
    private final Map<String, Instant> directRetransmitExpiresAt = new ConcurrentHashMap<>();
    private final Map<String, PendingRetransmitRequest> pendingRetransmitRequests = new ConcurrentHashMap<>();//保存发送方收到但尚未由用户确认的重传请求
    private final Map<String, CompletableFuture<OnlineUserSearchResultPacket>> onlineUserSearchFutures = new ConcurrentHashMap<>();
    private final Set<String> acceptedIncomingTransferIds = ConcurrentHashMap.newKeySet();
    private final Set<String> expiredDirectRetransmissionIds = ConcurrentHashMap.newKeySet();
    private final Set<String> canceledTransferIds = ConcurrentHashMap.newKeySet();//用来记录已经取消的transferId
    private final Set<String> acknowledgedCancelTransferIds = ConcurrentHashMap.newKeySet();


    public ClientTransferService(ClientConnectionManager clientConnectionManager, ClientProperties clientProperties, CryptoSupport cryptoSupport, NodeProperties nodeProperties, PushNotificationService pushNotificationService, TransferProperties transferProperties, TransferTaskRegistry transferTaskRegistry) {
        this.clientConnectionManager = clientConnectionManager;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.nodeProperties = nodeProperties;
        this.pushNotificationService = pushNotificationService;
        this.transferProperties = transferProperties;
        this.transferTaskRegistry = transferTaskRegistry;
        this.cleanupExecutorService.scheduleAtFixedRate(
                this::cleanupExpiredDirectRetransmissionContexts,
                DIRECT_RETRANSMIT_CLEANUP_INTERVAL_MINUTES,
                DIRECT_RETRANSMIT_CLEANUP_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    @PreDestroy
    public void shutdown()
    {
        cleanupExecutorService.shutdownNow();
        executorService.shutdownNow();
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


        String transferId= UUID.randomUUID().toString();//生成随机的传输Id
        String taskId= UUID.randomUUID().toString();//生成随机的任务Id

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
                    Instant.now(), //创建时间
                    TransportMode.SERVER_RELAY
            );
            registerTransferMode(transferId, TransportMode.SERVER_RELAY);
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

    public String sendFileDirect(Path filePath, ReceiverResponseQr receiver, PacketTransport transport)
    {
        if(transport == null || !transport.isActive())
        {
            throw new IllegalStateException("Direct peer transport is not active");
        }
        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        if (!Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Path is not a regular file: " + filePath);
        }

        String transferId = UUID.randomUUID().toString();
        String taskId = UUID.randomUUID().toString();
        try
        {
            long fileSize = Files.size(filePath);
            int totalBlocks = (int)((fileSize + transferProperties.getChunkSizeBytes() - 1) / transferProperties.getChunkSizeBytes());
            TransferTask task = new TransferTask(
                    taskId,
                    transferId,
                    TransferDirection.SEND,
                    filePath.getFileName().toString(),
                    filePath.toAbsolutePath().toString(),
                    receiver.getReceiverAccountId(),
                    fileSize,
                    totalBlocks,
                    Instant.now(),
                    TransportMode.DIRECT_PEER
            );
            registerTransferMode(transferId, TransportMode.DIRECT_PEER);
            task.updatePeerDeviceId(receiver.getReceiverDeviceId());
            task.updateStatus(TransferStatus.WAITING_FOR_TARGET, "Waiting for direct receiver acceptance");
            transferTaskRegistry.register(task);
            transferTransports.put(transferId, transport);
            pushNotificationService.publish("transfer-created", task);
            executorService.submit(() -> executeDirectSend(task, filePath, receiver));
            return taskId;
        }
        catch(IOException e)
        {
            throw new IllegalStateException("Unable to read file metadata", e);
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

    public void requestRetransmission(String taskIdOrTransferId)//接收方主动发起的重传请求
    {
        if(taskIdOrTransferId==null || taskIdOrTransferId.isBlank())
        {
            throw new IllegalArgumentException("taskIdOrTransferId is required");
        }
        TransferTask task = transferTaskRegistry.findByTaskId(taskIdOrTransferId)
                .or(() -> transferTaskRegistry.findByTransferId(taskIdOrTransferId))
                .orElseThrow(() -> new IllegalArgumentException("Transfer task not found: "+taskIdOrTransferId));
        ensureKnownTransferModeAllowsNetworkControl(task.getTransferId());
        if(task.getDirection()!=TransferDirection.RECEIVE)
        {
            throw new IllegalArgumentException("Retransmission must be requested by the receiver task");
        }
        if(task.getStatus()==TransferStatus.COMPLETED)
        {
            throw new IllegalStateException("Transfer is already completed");
        }
        throwIfDirectRetransmissionExpired(task.getTransferId());
        InboundTransferContext context = inboundTransferContexts.get(task.getTransferId());
        if(context==null)
        {
            if(resolveTransferMode(task.getTransferId()) == TransportMode.DIRECT_PEER)
            {
                throw new IllegalStateException(expiredDirectRetransmissionIds.contains(task.getTransferId())
                        ? "direct retransmission window expired"
                        : "direct runtime context unavailable because the program was restarted or the direct session expired");
            }
            throw new IllegalStateException("Receiver transfer context not found; this client no longer has the AES session key for transferId="+task.getTransferId());
        }

        int startBlockId;
        long receivedBytes;
        synchronized (context)
        {
            startBlockId = context.nextWriteBlockId;
            receivedBytes = context.receivedBytes;
        }
        canceledTransferIds.remove(task.getTransferId());
        task.updateProgress(receivedBytes, startBlockId);
        task.updateStatus(TransferStatus.TRANSFERRING, "Retransmission requested from block "+startBlockId);
        persistTask(task);
        pushNotificationService.publish("transfer-retransmit-requested", task);
        sendForTransfer(task.getTransferId(), new RetransmitRequestPacket(
                task.getTransferId(),
                startBlockId,
                "Receiver requests retransmission from block "+startBlockId
        ));
    }

    public void handleRetransmitRequest(RetransmitRequestPacket packet)//发送方处理接收方的重传请求
    {
        TransferTask task = transferTaskRegistry.findByTransferId(packet.getTransferId()).orElse(null);//查找本地任务和发送上下文；task:本地传输记录，2.content:发送端运行中的传输上下文
        OutboundTransferContext context = outboundTransferContexts.get(packet.getTransferId());
        TransportMode mode = task == null ? resolveTransferMode(packet.getTransferId()) : task.getTransportMode();
        if(task==null || task.getDirection()!=TransferDirection.SEND || context==null)//找不到任务，或者这个任务不是发送任务，或者发送上下文不存在就发一个拒绝请求的ACK包
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Sender cannot resume this transfer");
            return;
        }
        if(task.getStatus()==TransferStatus.COMPLETED)//判断任务是否已经完成
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Transfer is already completed");
            return;
        }
        if(isDirectRetransmissionExpired(packet.getTransferId()))
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "direct retransmission window expired");
            expireDirectRetransmissionRuntime(packet.getTransferId());
            return;
        }
        if((task.getStatus()==TransferStatus.CANCELED || isTransferCanceled(task.getTransferId())) && mode != TransportMode.DIRECT_PEER)//判断任务是否已经取消
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Transfer was canceled");
            return;
        }
        if(packet.getStartBlockId()<0 || packet.getStartBlockId()>task.getTotalBlocks())//检验起始编号是否合法
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Invalid retransmit start block");
            return;
        }

        Path filePath = Paths.get(task.getLocalPath());//确认原始文件还在发送方本地
        if(!Files.isRegularFile(filePath))//如果这个文件被删除，移动或者路径不是普通文件，就拒绝
        {
            sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Original file is not available on sender");
            return;
        }

        //校验通过，保存重传请求
        int startBlockId = packet.getStartBlockId();//重新读取传输起点
        canceledTransferIds.remove(task.getTransferId());//移除取消标记
        pendingRetransmitRequests.put(packet.getTransferId(), new PendingRetransmitRequest(packet, context, filePath));//保存pending重传请求
        task.updateStatus(TransferStatus.WAITING_FOR_ACCEPT, "Waiting for sender confirmation to retransmit from block "+startBlockId);//更新任务状态
        persistTask(task);
        pushNotificationService.publish("transfer-retransmit-request-received", Map.of(     //发起通知推送
                "transferId", packet.getTransferId(),
                "fileName", task.getFileName(),
                "startBlockId", startBlockId,
                "reason", packet.getReason()==null ? "" : packet.getReason()
        ));
    }

    public Map<String, RetransmitRequestPacket> pendingRetransmitRequests()
    {
        Map<String, RetransmitRequestPacket> result = new LinkedHashMap<>();
        for(Map.Entry<String, PendingRetransmitRequest> entry : pendingRetransmitRequests.entrySet())
        {
            result.put(entry.getKey(), entry.getValue().packet());
        }
        return Map.copyOf(result);
    }

    public void acceptRetransmission(String transferId) //处理发送方确认接收重传请求的函数
    {
        PendingRetransmitRequest pending = takePendingRetransmitRequest(transferId);//根据transferId取除之前暂存的重传请求
        RetransmitRequestPacket packet = pending.packet();//从pending里拿到原始的重传请求包(程序目前不支持程序退出后的重传请求)
        sendRetransmitAck(packet.getTransferId(), true, packet.getStartBlockId(), "Retransmission accepted by sender");//发送方给接收方回一个重传确认包(同意)
        executorService.submit(() -> executeRetransmit(pending.context(), pending.filePath(), packet.getStartBlockId()));//重传任务提交到线程池异步执行
    }

    public void rejectRetransmission(String transferId)//处理发送方拒绝重传的请求
    {
        PendingRetransmitRequest pending = takePendingRetransmitRequest(transferId);//还是根据transferId取除之前暂存的重传请求
        RetransmitRequestPacket packet = pending.packet();//还是从pending里拿到原始的重传请求包
        sendRetransmitAck(packet.getTransferId(), false, packet.getStartBlockId(), "Retransmission rejected by sender");//发送方给接收方回一个重传确认包(拒绝)
        TransferTask task = pending.context().transferTask;//从发送上下文处拿到对应的发送任务
        task.updateStatus(TransferStatus.FAILED, "Retransmission rejected by sender");//更新任务状态
        persistTask(task);
        outboundTransferContexts.remove(packet.getTransferId());//从传输上下文表里移除这个传输上下文
        for(CompletableFuture<Boolean> ackFuture : pending.context().ackFutures.values())//一些block正在等待ACK时，会立即得false
        {
            ackFuture.complete(false);
        }
        pending.context().ackFutures.clear();
        pushNotificationService.publish("transfer-retransmit-rejected", task);//通知
    }

    private PendingRetransmitRequest takePendingRetransmitRequest(String transferId)
    {
        if(transferId==null || transferId.isBlank())
        {
            throw new IllegalArgumentException("transferId is required");
        }
        PendingRetransmitRequest pending = pendingRetransmitRequests.remove(transferId);
        if(pending==null)
        {
            throw new IllegalArgumentException("Pending retransmission request not found: "+transferId);
        }
        return pending;
    }

    public void handleRetransmitAck(RetransmitAckPacket packet)//接收方处理发送方的重传请求结果
    {
        transferTaskRegistry.findByTransferId(packet.getTransferId()).ifPresent(task -> {
            if(packet.isAccepted())
            {
                canceledTransferIds.remove(packet.getTransferId());//移除取消标记
                task.updateStatus(TransferStatus.TRANSFERRING, "Retransmission accepted from block "+packet.getStartBlockId());//更新任务状态为正在传输
                pushNotificationService.publish("transfer-retransmit-accepted", task);//通知
            }
            else
            {
                task.updateStatus(TransferStatus.FAILED, packet.getMessage());
                pushNotificationService.publish("transfer-retransmit-rejected", task);
            }
            persistTask(task);
        });
    }

    public void cancelTransfer(String taskIdOrTransferId)//处理中断传输的函数；支持taskId和transferId
    {
        if(taskIdOrTransferId==null || taskIdOrTransferId.isBlank())//校验参数
        {
            throw new IllegalArgumentException("taskIdOrTransferId is required");
        }

        TransferTask task = transferTaskRegistry.findByTaskId(taskIdOrTransferId)       //按taskId查找任务
                .or(() -> transferTaskRegistry.findByTransferId(taskIdOrTransferId))    //按transferId查找任务
                .orElse(null);
        if(task==null)
        {
            PendingIncomingTransferRequest pendingRequest = pendingIncomingTransferRequests.get(taskIdOrTransferId);
            IncomingTransferRequestPacket request = pendingRequest == null ? null : pendingRequest.packet();
            if(request==null)   //任务不存在的情况
            {
                throw new IllegalArgumentException("Transfer task not found: "+taskIdOrTransferId);
            }

            ensureKnownTransferModeAllowsNetworkControl(request.getTransferId());
            //告诉发送方这次传输请求被取消了
            sendForTransfer(request.getTransferId(), new ReceiverDeviceSelectionPacket(
                    request.getTransferId(),
                    false,
                    "Transfer canceled before accept"
            ));
            pendingIncomingTransferRequests.remove(request.getTransferId());
            if(resolveTransferMode(request.getTransferId()) == TransportMode.DIRECT_PEER)
            {
                closeDirectTransport(request.getTransferId());
                clearDirectRuntimeState(request.getTransferId());
            }
            return;
        }

        ensureKnownTransferModeAllowsNetworkControl(task.getTransferId());
        if(task.getStatus()!=null && task.getStatus().isTerminal())     //如果任务已经完成了，就直接返回
        {
            return;
        }

        String reason = "Transfer canceled locally";    //取消原因
        boolean retainDirectRuntime = shouldRetainDirectRuntimeOnStop(task.getTransferId(), TransferStatus.CANCELED);
        sendForTransfer(task.getTransferId(), new TransferCancelPacket(task.getTransferId(), reason));
        cancelLocalTransfer(task.getTransferId(), reason, retainDirectRuntime);
        task.updateStatus(TransferStatus.CANCELED, reason);
        persistTask(task);  //保存状态
        pushNotificationService.publish("transfer-cancelled", task);    //通知其他模块任务已经取消
        if(retainDirectRuntime)
        {
            retainDirectRetransmissionRuntime(task.getTransferId());
        }

    }

    public void handleTransferCancel(TransferCancelPacket packet)   //取消传输任务后，执行本地清理
    {
        String reason = packet.getReason()==null || packet.getReason().isBlank()
                ? "Transfer canceled by peer"
                : packet.getReason();//确定传输任务取消的原因
        removePendingIncomingTransferRequest(packet.getTransferId());
        boolean firstCancel = canceledTransferIds.add(packet.getTransferId());
        if(!firstCancel)
        {
            sendCancelAckIfSender(packet.getTransferId(), reason);
            return;
        }

        boolean retainDirectRuntime = shouldRetainDirectRuntimeOnStop(packet.getTransferId(), TransferStatus.CANCELED);
        cancelLocalTransfer(packet.getTransferId(), reason, retainDirectRuntime);//执行本地取消逻辑，停止正在发送或接收的数据流，关闭文件句柄，释放缓存区
        transferTaskRegistry.findByTransferId(packet.getTransferId()).ifPresent(task -> {   //根据transferId查找本地任务
            if(task.getStatus()==null || !task.getStatus().isTerminal())
            {
                task.updateStatus(TransferStatus.CANCELED, reason);
                persistTask(task);
                pushNotificationService.publish("transfer-cancelled", task);
            }
        });
        if(retainDirectRuntime)
        {
            retainDirectRetransmissionRuntime(packet.getTransferId());
        }
        sendCancelAckIfSender(packet.getTransferId(), reason);//发送传输取消ACK数据包
    }

    public void handleTransferCancelAck(TransferCancelAckPacket packet)
    {
        if(!acknowledgedCancelTransferIds.add(packet.getTransferId()))
        {
            return;
        }

        transferTaskRegistry.findByTransferId(packet.getTransferId()).ifPresent(task -> {
            String message = "Cancel acknowledged by peer";
            if(packet.getAckByDeviceId()!=null && !packet.getAckByDeviceId().isBlank())
            {
                message = "Cancel acknowledged by peer device: "+packet.getAckByDeviceId();
            }
            if(packet.getMessage()!=null && !packet.getMessage().isBlank())
            {
                message = message + " (" + packet.getMessage() + ")";
            }
            task.updateStatus(task.getStatus(), message);
            persistTask(task);
            pushNotificationService.publish("transfer-cancel-acknowledged", task);
        });
    }

    //接收端收到文件发送请求时的处理函数
    public void handleIncomingOffer(FileOfferPacket packet) throws GeneralSecurityException, IOException
    {
        TransportMode mode = resolveIncomingOfferMode(packet.getTransferId());
        if(mode == TransportMode.UNKNOWN)
        {
            throw new IllegalStateException("Transfer history record lacks transfer mode: "+packet.getTransferId());
        }
        if(mode == TransportMode.DIRECT_PEER && !acceptedIncomingTransferIds.remove(packet.getTransferId()))
        {
            sendForTransfer(packet.getTransferId(), new FileAcceptPacket(false, "Transfer was not accepted by receiver", packet.getTransferId()));
            closeDirectTransport(packet.getTransferId());
            clearDirectRuntimeState(packet.getTransferId());
            return;
        }
        if(mode == TransportMode.SERVER_RELAY)
        {
            registerTransferMode(packet.getTransferId(), TransportMode.SERVER_RELAY);
        }

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
                Instant.now(),
                mode
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
        sendForTransfer(packet.getTransferId(), new FileAcceptPacket(true, "Auto accepted", packet.getTransferId()));//返回接收信息
    }

    //接收方设备主动拒绝传输请求
    public void rejectIncomingTransfer(String transferId)
    {
        PendingIncomingTransferRequest pendingRequest = pendingIncomingTransferRequests.get(transferId);
        IncomingTransferRequestPacket request = pendingRequest == null ? null : pendingRequest.packet();
        if(request==null)
        {
            throw new IllegalStateException("Incoming transfer request not found: "+transferId);
        }
        ensureKnownTransferModeAllowsNetworkControl(transferId);
        sendForTransfer(transferId, new ReceiverDeviceSelectionPacket(transferId, false, "Rejected by receiver device"));
        removePendingIncomingTransferRequest(transferId);
        if(resolveTransferMode(transferId) == TransportMode.DIRECT_PEER)
        {
            closeDirectTransport(transferId);
            clearDirectRuntimeState(transferId);
        }
    }

    //处理接收到的文件数据块
    public void handleIncommingBlock(FileBlockPacket packet) throws GeneralSecurityException, IOException
    {
        if(isTransferCanceled(packet.getTransferId()))
        {
            sendForTransfer(packet.getTransferId(), new AckPacket(packet.getBlockId(),false,packet.getTransferId()));
            return;
        }
        InboundTransferContext context = inboundTransferContexts.get(packet.getTransferId());//根据transferId找到上下文
        if(context==null)//接收方并不知道本次传输
        {
            sendForTransfer(packet.getTransferId(), new AckPacket(packet.getBlockId(),false,packet.getTransferId()));
            return;
        }

        //解密数据块
        byte[] plain=cryptoSupport.decryptChunk(packet.getNonce(),packet.getCiphertext(),packet.getTag(),context.secretKey);
        boolean accepted;
        boolean ackNow;
        synchronized (context)
        {
            accepted=context.acceptDecryptedBlock(packet.getBlockId(), plain);
            if(accepted && context.shouldDelayAck())
            {
                context.delayAck(packet.getBlockId());
                ackNow = false;
            }
            else
            {
                ackNow = true;
            }
        }

        // 缓存压力低时立即ACK；压力高时由后台写盘降到低水位后释放ACK，形成接收端背压。
        if(ackNow)
        {
            sendForTransfer(packet.getTransferId(), new AckPacket(packet.getBlockId(),true,packet.getTransferId()));
        }

        if(accepted)
        {
            scheduleInboundFlush(packet.getTransferId(), context);
        }
    }

    //安排后台任务，把接收缓存里的数据写入文件
    private void scheduleInboundFlush(String transferId, InboundTransferContext context)
    {
        synchronized (context) //加锁检查状态，因为多个文件块可能连续到达，多个线程可能同时想安排写文件任务，所以要锁住同一个接收上下文。
        {
            if(context.isClosed() || context.isFlushScheduled())    //避免重复提交任务；context.isClosed表示接收任务已经接收不需要再写；context.isFlushScheduled标识已经有一个后台写文件任务在排队或运行了，不要再提交新的
            {
                return;
            }
            context.markFlushScheduled();//标记已经有写入任务
        }
        executorService.submit(() -> flushInboundBlocks(transferId, context));//并提交后台任务
    }

    //接收端后台写文件任务，把已经解密且放进内存缓存里的块，按顺序写进目标文件，并更新接收进度，判断文件是否接收完成，释放延迟的ACK，推送进度或完成事件，写文件失败时标记任务失败
    private void flushInboundBlocks(String transferId, InboundTransferContext context)
    {
        boolean complete = false;
        boolean publishProgress = false;
        boolean reschedule = false;
        List<Integer> delayedAckBlockIds = List.of();
        try
        {
            synchronized (context)//锁住当前接收任务，防止多个线程同时修改
            {
                if(context.isClosed())//如果任务已经结束，就不再继续写了，直接退出
                {
                    context.clearFlushScheduled();
                    return;
                }

                boolean wrote = context.flushContiguousBlocks();//真正写文件的地方
                if(!wrote)//必须按顺序写
                {
                    context.clearFlushScheduled();
                    return;
                }

                //更新任务进度
                context.transferTask.updateProgress(context.receivedBytes, context.receivedBlocks);
                context.transferTask.updateStatus(TransferStatus.TRANSFERRING, "Receiving blocks");

                complete = context.isCompleted();
                if(complete)//判断是否写入完成
                {
                    context.outputStream.flush();
                    context.outputStream.close();
                    context.markClosed();
                    context.transferTask.updateStatus(TransferStatus.COMPLETED, "File received");
                    delayedAckBlockIds = context.drainDelayedAcks();
                }
                else
                {
                    //未完成则判断是否到了进度推送时间，避免每块都推送，如果缓存压力已经下降到低水位以下，就释放延迟 ACK
                    publishProgress = context.shouldPublishProgress();
                    if(context.shouldReleaseDelayedAcks())
                    {
                        delayedAckBlockIds = context.drainDelayedAcks();
                    }
                }

                //表示当前后台写任务结束了。
                context.clearFlushScheduled();
                reschedule = !context.isClosed() && context.hasContiguousPendingBlock();
            }

            //如果完成就发出所有延迟 ACK，从接收上下文表里移除这个任务，推送完成事件，持久化任务状态
            if(complete)
            {
                sendDelayedAcks(transferId, delayedAckBlockIds);
                inboundTransferContexts.remove(transferId, context);
                pushNotificationService.publish("transfer-complete", context.transferTask);
                persistTask(context.transferTask);
                closeDirectTransport(transferId);
                clearDirectRuntimeState(transferId);
                return;
            }

            //没有完成，但有延迟 ACK 要释放，也在这里发。
            sendDelayedAcks(transferId, delayedAckBlockIds);

            if(publishProgress)
            {
                pushNotificationService.publish("transfer-progress", context.transferTask);
                persistTask(context.transferTask);
            }

            if(reschedule)//如果还有连续块能写，就再安排一次后台写任务
            {
                scheduleInboundFlush(transferId, context);
            }
        }
        catch(IOException e)
        {
            failInboundTransfer(transferId, context, e);
        }
    }

    //将之前被推迟的ACK补发出来
    private void sendDelayedAcks(String transferId, List<Integer> blockIds)
    {
        for(Integer blockId : blockIds)//哪些块的ACK之前没有立刻发送，就现在补发
        {
            sendForTransfer(transferId, new AckPacket(blockId, true, transferId));
        }
    }

    //处理接收端文件写入失败
    private void failInboundTransfer(String transferId, InboundTransferContext context, Exception e)
    {
        boolean retainDirectRuntime = shouldRetainDirectRuntimeOnStop(transferId, TransferStatus.FAILED);
        if(!retainDirectRuntime && !inboundTransferContexts.remove(transferId, context))//将该结束任务从正在接收的上下文表里移除
        {
            return;
        }

        synchronized (context)
        {
            if(!retainDirectRuntime)
            {
                context.markClosed();//标记关闭
                context.closeQuietly();//2.关闭输出文件流
            }
            else
            {
                context.clearBufferedBlocks();
            }
            context.transferTask.updateStatus(TransferStatus.FAILED, "Receive write failed: "+e.getMessage());//将任务状态改成失败
        }

        persistTask(context.transferTask);
        pushNotificationService.publish("transfer-failed", context.transferTask);

        if(retainDirectRuntime)
        {
            retainDirectRetransmissionRuntime(transferId);
        }

        if(resolveTransferMode(transferId) != TransportMode.UNKNOWN)
        {
            sendForTransfer(transferId, new TransferCancelPacket(transferId, "Receiver write failed: "+e.getMessage()));
        }
    }

    //获取当前客户端的连接状态
    public Map<String, Object> clientStatus()
    {
        return clientConnectionManager.currentStatus();
    }

    //真正执行发送文件的函数
    //使用固定发送窗口：最多同时发送 sendWindowSize 个未确认的数据块
    private void executeSend(TransferTask task, Path filePath, String targetAccountId)
    {
        OutboundTransferContext context = null;
        try
        {
            //等待服务端返回目标账号下的具体接收设备
            CompletableFuture<SelectedReceiverDevice> recipientFuture=new CompletableFuture<>();
            deviceSelectionFutures.put(task.getTransferId(), recipientFuture);

            //告诉服务端，文件的信息
            sendForTransfer(task.getTransferId(), new TransferRequestPacket(
                            task.getTransferId(),
                            targetAccountId,
                            task.getFileName(),
                            task.getTotalBytes(),
                            task.getTotalBlocks()
                    )
            );

            //等待服务端的设备选择结果
            SelectedReceiverDevice selectedReceiverDevice=recipientFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            task.updatePeerDeviceId(selectedReceiverDevice.getDeviceId());
            persistTask(task);

            String recipientPublicKey=selectedReceiverDevice.getPublicKey();//获取接收方公钥
            SecretKey secretKey=cryptoSupport.generateAESKey();//生成AES会话密钥（本次传输文件专用的AES密钥，一次传输任务一个AES密钥）
            String encryptedSessionKey=cryptoSupport.encryptAESKeyForReceiver(secretKey, recipientPublicKey);//加密AES会话密钥（接收方的公钥加密该AES密钥，待接收方收到后用接收方自己的私钥解开AES密钥，再配合nonce解密文件块）

            //本次发送任务的上下文环境
            context=new OutboundTransferContext(secretKey, task,recipientPublicKey);//AES密钥，TransferTask, acceptFuture, ackFuture
            outboundTransferContexts.put(task.getTransferId(),context);//添加文件信息的数据包

            task.updateStatus(TransferStatus.WAITING_FOR_ACCEPT, "Waiting for receiver acceptance");
            persistTask(task);

            //发送文件信息的数据包
            sendForTransfer(task.getTransferId(),
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

            //等待接收方接收
            FileAcceptPacket fileAcceptPacket=context.acceptFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(!fileAcceptPacket.isAccept())//接收方拒绝接收
            {
                task.updateStatus(TransferStatus.REJECTED, fileAcceptPacket.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-rejected", task);
                outboundTransferContexts.remove(task.getTransferId());
                return;
            }

            //确认接收
            task.updateStatus(TransferStatus.TRANSFERRING, "Sending blocks");
            persistTask(task);

            sendBlocksFrom(task, filePath, secretKey, context, 0);//发送窗口

            throwIfCanceled(task);
            task.updateStatus(TransferStatus.COMPLETED, "File sent");//所有块都Ack成功再更新任务信息
            persistTask(task);//保存任务信息
            pushNotificationService.publish("transfer-complete", task);//通知任务完成
        }
        catch(Exception e)
        {
            log.info("TransferStatus: FAILED, "+e.getMessage());
            Throwable cause=e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            if(cause instanceof TransferCanceledException || isTransferCanceled(task.getTransferId()))
            {
                if(cause instanceof TransferCanceledException transferCanceledException)
                {
                    task.updatePeerDeviceId(transferCanceledException.getDeviceId());
                }
                task.updateStatus(TransferStatus.CANCELED, canceledMessage(cause));
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
        finally
        {
            if(task.getStatus()!=TransferStatus.FAILED)
            {
                outboundTransferContexts.remove(task.getTransferId());
            }
            deviceSelectionFutures.remove(task.getTransferId());
            if(context!=null)
            {
                context.ackFutures.clear();
            }
        }

    }

    private void executeDirectSend(TransferTask task, Path filePath, ReceiverResponseQr receiver)
    {
        OutboundTransferContext context = null;
        try
        {
            CompletableFuture<ReceiverDeviceSelectionPacket> selectionFuture = new CompletableFuture<>();
            directReceiverSelectionFutures.put(task.getTransferId(), selectionFuture);
            sendForTransfer(task.getTransferId(), new IncomingTransferRequestPacket(
                    task.getTransferId(),
                    nodeProperties.getDeviceId(),
                    receiver.getReceiverAccountId(),
                    task.getFileName(),
                    task.getTotalBytes(),
                    task.getTotalBlocks()
            ));

            ReceiverDeviceSelectionPacket selection = selectionFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(!selection.isAccepted())
            {
                task.updateStatus(TransferStatus.REJECTED, selection.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-rejected", task);
                return;
            }

            String recipientPublicKey = receiver.getReceiverPublicKey();
            SecretKey secretKey = cryptoSupport.generateAESKey();
            String encryptedSessionKey = cryptoSupport.encryptAESKeyForReceiver(secretKey, recipientPublicKey);
            context = new OutboundTransferContext(secretKey, task, recipientPublicKey);
            outboundTransferContexts.put(task.getTransferId(), context);

            task.updateStatus(TransferStatus.WAITING_FOR_ACCEPT, "Waiting for receiver acceptance");
            persistTask(task);

            sendForTransfer(task.getTransferId(), new FileOfferPacket(
                    encryptedSessionKey,
                    task.getFileName(),
                    task.getTotalBytes(),
                    recipientPublicKey,
                    clientConnectionManager.getLocalPublicKey(),
                    task.getTotalBlocks(),
                    task.getTransferId()
            ));

            FileAcceptPacket fileAcceptPacket = context.acceptFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(!fileAcceptPacket.isAccept())
            {
                task.updateStatus(TransferStatus.REJECTED, fileAcceptPacket.getMessage());
                persistTask(task);
                pushNotificationService.publish("transfer-rejected", task);
                return;
            }

            task.updateStatus(TransferStatus.TRANSFERRING, "Sending blocks");
            persistTask(task);
            sendBlocksFrom(task, filePath, secretKey, context, 0);
            throwIfCanceled(task);
            task.updateStatus(TransferStatus.COMPLETED, "File sent");
            persistTask(task);
            pushNotificationService.publish("transfer-complete", task);
        }
        catch(Exception e)
        {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            if(cause instanceof TransferCanceledException || isTransferCanceled(task.getTransferId()))
            {
                task.updateStatus(TransferStatus.CANCELED, canceledMessage(cause));
                pushNotificationService.publish("transfer-cancelled", task);
            }
            else
            {
                task.updateStatus(TransferStatus.FAILED, e.getMessage());
                pushNotificationService.publish("transfer-failed", task);
            }
            persistTask(task);
        }
        finally
        {
            directReceiverSelectionFutures.remove(task.getTransferId());
            if(task.getStatus() == TransferStatus.COMPLETED || task.getStatus() == TransferStatus.REJECTED)
            {
                outboundTransferContexts.remove(task.getTransferId());
                closeDirectTransport(task.getTransferId());
                clearDirectRuntimeState(task.getTransferId());
            }
            else if(shouldRetainDirectRuntimeOnStop(task.getTransferId(), task.getStatus()))
            {
                retainDirectRetransmissionRuntime(task.getTransferId());
            }
            else
            {
                outboundTransferContexts.remove(task.getTransferId());
                closeDirectTransport(task.getTransferId());
                clearDirectRuntimeState(task.getTransferId());
            }
            if(context != null)
            {
                context.ackFutures.clear();
            }
        }
    }

    private void executeRetransmit(OutboundTransferContext context, Path filePath, int startBlockId)//正在执行断点重传的函数
    {
        synchronized (context)
        {
            TransferTask task = context.transferTask;//取出任务
            try
            {
                context.ackFutures.clear();//清空旧的ACK等待记录
                task.updateStatus(TransferStatus.TRANSFERRING, "Retransmitting from block "+startBlockId);//更新任务状态
                persistTask(task);
                pushNotificationService.publish("transfer-retransmit-started", task);
                sendBlocksFrom(task, filePath, context.secretKey, context, startBlockId);//读取原文件，按块加密，然后发送给接收方
                throwIfCanceled(task);//检查任务是否在过程中被取消
                task.updateStatus(TransferStatus.COMPLETED, "File sent");
                persistTask(task);
                pushNotificationService.publish("transfer-complete", task);//更新任务状态为完成
                outboundTransferContexts.remove(task.getTransferId());//清理发送端上下文
                if(task.getTransportMode() == TransportMode.DIRECT_PEER)
                {
                    closeDirectTransport(task.getTransferId());
                    clearDirectRuntimeState(task.getTransferId());
                }
                context.ackFutures.clear();
            }
            catch(Exception e)
            {
                Throwable cause=e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                if(cause instanceof TransferCanceledException || isTransferCanceled(task.getTransferId()))
                {
                    task.updateStatus(TransferStatus.CANCELED, canceledMessage(cause));
                    if(shouldRetainDirectRuntimeOnStop(task.getTransferId(), TransferStatus.CANCELED))
                    {
                        retainDirectRetransmissionRuntime(task.getTransferId());
                    }
                    else
                    {
                        outboundTransferContexts.remove(task.getTransferId());
                    }
                    pushNotificationService.publish("transfer-cancelled", task);
                }
                else
                {
                    task.updateStatus(TransferStatus.FAILED, e.getMessage());
                    if(shouldRetainDirectRuntimeOnStop(task.getTransferId(), TransferStatus.FAILED))
                    {
                        retainDirectRetransmissionRuntime(task.getTransferId());
                    }
                    pushNotificationService.publish("transfer-failed", task);
                }
                persistTask(task);
                context.ackFutures.clear();
            }
        }
    }

    //发送文件块的函数
    private void sendBlocksFrom(TransferTask task, Path filePath, SecretKey secretKey, OutboundTransferContext context, int startBlockId) throws Exception
    {
        try(InputStream inputStream=Files.newInputStream(filePath))
        {
            byte[] buffer=new byte[transferProperties.getChunkSizeBytes()];
            Map<Integer, CompletableFuture<Boolean>> pendingAckFutures=new HashMap<>();//blockId对应的该block对应的ACK Future
            Map<Integer, Integer> pendingAckBlockSizes=new HashMap<>();//blockId对应的该block的原始字节长度
            int blockId=startBlockId;
            int length;
            long acknowledgedBytes=Math.min(task.getTotalBytes(), (long)startBlockId * transferProperties.getChunkSizeBytes());
            int acknowledgedBlocks=startBlockId;

            if(startBlockId>0)
            {
                inputStream.skipNBytes((long)startBlockId * transferProperties.getChunkSizeBytes());
                updateSendProgress(task, acknowledgedBytes, acknowledgedBlocks);
            }

            while((length=inputStream.read(buffer))!=-1)//分块读取，每次读取一块
            {
                throwIfCanceled(task);
                byte[] plain=new byte[length];
                System.arraycopy(buffer,0,plain,0,length);
                AesGcmChunk encryptedChunk=cryptoSupport.encryptChunk(plain,secretKey);//加密该数据块()tag,nonce, ciphertext
                CompletableFuture<Boolean> ackFuture=new CompletableFuture<>();//给当前块创建ACK Future
                context.ackFutures.put(blockId, ackFuture);//保存ACK Future，Netty回调线程使用，收到ACKPacket后，handleAck会根据transferId+blockId找到它并complete
                pendingAckFutures.put(blockId, ackFuture);//给发送线程使用，判断哪些块是已经确认了的
                pendingAckBlockSizes.put(blockId, length);

                sendForTransfer(task.getTransferId(),
                        new FileBlockPacket(
                                blockId,
                                encryptedChunk.ciphertext(),
                                encryptedChunk.nonce(),
                                encryptedChunk.tag(),
                                task.getTransferId()
                        )
                );

                blockId++;//准备下一块

                if(pendingAckFutures.size() >= transferProperties.getSendWindowSize())//未确认的块达到窗口上限后，等待任意一个Ack回来再继续发送
                {
                    int ackedBlockId=waitForAnyAck(pendingAckFutures);//等待任意一个ACK，会阻塞线程
                    acknowledgedBytes+=pendingAckBlockSizes.remove(ackedBlockId);//确认字节长度增加
                    acknowledgedBlocks++;
                    updateSendProgress(task, acknowledgedBytes, acknowledgedBlocks);//更新进度
                }
            }

            //如果文件块已经全部发送出去了，但还有一些块的ACK没收到，就要继续等待直到所有已发送的块都确认收到
            while(!pendingAckFutures.isEmpty())
            {
                throwIfCanceled(task);//检查该传输任务是否被取消
                int ackedBlockId=waitForAnyAck(pendingAckFutures);
                acknowledgedBytes+=pendingAckBlockSizes.remove(ackedBlockId);
                acknowledgedBlocks++;
                updateSendProgress(task, acknowledgedBytes, acknowledgedBlocks);
            }
        }
    }

    private void sendRetransmitAck(String transferId, boolean accepted, int startBlockId, String message)
    {
        sendForTransfer(transferId, new RetransmitAckPacket(transferId, accepted, startBlockId, message));
    }

    //等待异步ACK确认信息
    //如果该块被接收方确认收到之后那么就从等待队列里面移除
    private int waitForAnyAck(Map<Integer, CompletableFuture<Boolean>> pendingAckFutures) throws Exception  //等待异步ACK确认信息
    {
        Integer completedBlockId=findCompletedAckBlockId(pendingAckFutures);//检查是否有已经完成的Ack
        if(completedBlockId==null)//没有以及完成的ACK，就等到任意一个Future完成
        {
            //如果没有在规定时间内返回任何一个ACK,就认为是传输异常
            CompletableFuture.anyOf(pendingAckFutures.values().toArray(new CompletableFuture[0]))
                    .get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            completedBlockId=findCompletedAckBlockId(pendingAckFutures);
        }

        if(completedBlockId==null)
        {
            throw new IllegalStateException("ACK completed but block id was not found");
        }

        //找到完成的blockId后，从pending map中移出并判断是否成功
        CompletableFuture<Boolean> ackFuture=pendingAckFutures.remove(completedBlockId);
        Boolean success=ackFuture.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
        if(!Boolean.TRUE.equals(success))//Ack返回失败，发送任务进入catch最终状态变成Failed
        {
            throw new IllegalStateException("ACK failed for block "+completedBlockId);
        }
        return completedBlockId;
    }

    //扫描当前发送窗口是否有已经完成的Future,找到就返回它的块号
    private Integer findCompletedAckBlockId(Map<Integer, CompletableFuture<Boolean>> pendingAckFutures)
    {
        for(Map.Entry<Integer, CompletableFuture<Boolean>> entry:pendingAckFutures.entrySet())
        {
            if(entry.getValue().isDone())
            {
                return entry.getKey();
            }
        }
        return null;
    }

    //更新发送进度
    private void updateSendProgress(TransferTask task, long sentBytes, int sentBlocks)
    {
        task.updateProgress(sentBytes, sentBlocks);
        persistTask(task);
        pushNotificationService.publish("transfer-progress", task);
    }

    private void cancelLocalTransfer(String transferId, String reason, boolean retainDirectRuntime)  //处理本地内存状态，等待中的异步任务，传输上下文和资源关闭
    {
        canceledTransferIds.add(transferId);//记录到取消集合
        pendingRetransmitRequests.remove(transferId);

        CompletableFuture<SelectedReceiverDevice> deviceSelectionFuture = deviceSelectionFutures.remove(transferId);
        if(deviceSelectionFuture!=null)
        {
            deviceSelectionFuture.completeExceptionally(new TransferCanceledException(reason, null));
        }//清理接收设备选择的等待任务

        OutboundTransferContext outboundContext = retainDirectRuntime ? outboundTransferContexts.get(transferId) : outboundTransferContexts.remove(transferId);//清理发送端上下文
        if(outboundContext!=null)
        {
            outboundContext.acceptFuture.completeExceptionally(new TransferCanceledException(reason, null));    //如果发送端还在等待接收方传输，就让这个等待异常结束
            for(CompletableFuture<Boolean> ackFuture : outboundContext.ackFutures.values())
            {
                ackFuture.complete(false);//处理等待ACK的future对象
            }
            outboundContext.ackFutures.clear();
        }

        InboundTransferContext inboundContext = retainDirectRuntime ? inboundTransferContexts.get(transferId) : inboundTransferContexts.remove(transferId);
        if(inboundContext!=null)
        {
            if(!retainDirectRuntime)
            {
                inboundContext.closeQuietly();//清理接收端上下文，标记transferId已经取消，2.删除待重传请求，3.取消等待选择接收设备的future,4.清理发送端上下文，5.让等待接收和等待ACK的异步流程结束，6.关闭资源
            }
            else
            {
                inboundContext.clearBufferedBlocks();
            }
        }
        if(!retainDirectRuntime)
        {
            closeDirectTransport(transferId);
        }
    }

    private boolean isTransferCanceled(String transferId)
    {
        return canceledTransferIds.contains(transferId);
    }

    private void throwIfCanceled(TransferTask task)
    {
        if(isTransferCanceled(task.getTransferId()))
        {
            throw new TransferCanceledException("Transfer canceled locally", task.getPeerDeviceId());
        }
    }

    private String canceledMessage(Throwable cause)
    {
        String message = cause == null ? null : cause.getMessage();
        return message == null || message.isBlank() ? "Transfer canceled" : message;
    }

    private void sendCancelAckIfSender(String transferId, String reason)
    {
        TransferTask task = transferTaskRegistry.findByTransferId(transferId).orElse(null);
        if(task == null || task.getDirection() != TransferDirection.SEND)
        {
            return;
        }
        sendForTransfer(transferId, new TransferCancelAckPacket(
                transferId,
                nodeProperties.getDeviceId(),
                TransferStatus.CANCELED.name(),
                reason == null || reason.isBlank() ? "Transfer cancel acknowledged" : "Transfer cancel acknowledged: "+reason
        ));
    }

    //原始备份版本：发送一块等待一块的ACK结果(第一版)
    @SuppressWarnings("unused")
    private void executeSendBlockingAckBackup(TransferTask task, Path filePath, String targetAccountId)
    {
        OutboundTransferContext context = null;
        try
        {
            //准备接收方的公钥
            CompletableFuture<SelectedReceiverDevice> recipientFuture=new CompletableFuture<>();
            deviceSelectionFutures.put(task.getTransferId(), recipientFuture);

            //发送设备选择请求
            sendForTransfer(task.getTransferId(), new TransferRequestPacket(
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
            sendForTransfer(task.getTransferId(),
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
                    sendForTransfer(task.getTransferId(),
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

    public void registerDirectTransfer(String transferId, PacketTransport transport)
    {
        if(transferId == null || transferId.isBlank() || transport == null)
        {
            return;
        }
        registerTransferMode(transferId, TransportMode.DIRECT_PEER);
        transferTransports.put(transferId, transport);
    }

    private void sendForTransfer(String transferId, Packet packet)
    {
        TransportMode mode = resolveTransferMode(transferId);
        if(mode == TransportMode.UNKNOWN)
        {
            throw new IllegalStateException("Transfer history record lacks transfer mode: "+transferId);
        }
        PacketTransport transport = transferTransports.get(transferId);
        if(mode == TransportMode.DIRECT_PEER)
        {
            if(transport != null && transport.isActive())
            {
                transport.send(packet);
                return;
            }
            String message = expiredDirectRetransmissionIds.contains(transferId)
                    ? "direct retransmission window expired"
                    : "direct runtime context unavailable because the program was restarted or the direct session expired";
            throw new IllegalStateException(message);
        }
        new ServerRelayTransport(clientConnectionManager).send(packet);
    }

    private void registerTransferMode(String transferId, TransportMode mode)
    {
        if(transferId == null || transferId.isBlank() || mode == null || mode == TransportMode.UNKNOWN)
        {
            return;
        }
        transferModes.put(transferId, mode);
    }

    private TransportMode resolveTransferMode(String transferId)
    {
        if(transferId == null || transferId.isBlank())
        {
            return TransportMode.UNKNOWN;
        }
        TransportMode mode = transferModes.get(transferId);
        if(mode != null)
        {
            return mode;
        }
        return transferTaskRegistry.findByTransferId(transferId)
                .map(TransferTask::getTransportMode)
                .orElse(TransportMode.UNKNOWN);
    }

    private TransportMode resolveIncomingOfferMode(String transferId)
    {
        TransportMode mode = resolveTransferMode(transferId);
        if(mode == TransportMode.UNKNOWN && !transferTaskRegistry.findByTransferId(transferId).isPresent())
        {
            return TransportMode.SERVER_RELAY;
        }
        return mode;
    }

    private void ensureKnownTransferModeAllowsNetworkControl(String transferId)
    {
        if(resolveTransferMode(transferId) == TransportMode.UNKNOWN)
        {
            throw new IllegalStateException("Transfer history record lacks transfer mode: "+transferId);
        }
    }

    private void removeTransferTransportIfTerminal(String transferId)
    {
        transferTaskRegistry.findByTransferId(transferId).ifPresent(task -> {
            if(task.getStatus() != null && task.getStatus().isTerminal())
            {
                transferTransports.remove(transferId);
            }
        });
    }

    private void closeDirectTransport(String transferId)
    {
        PacketTransport transport = transferTransports.remove(transferId);
        if(transport instanceof DirectPeerTransport directPeerTransport)
        {
            directPeerTransport.channel().close();
        }
    }

    private boolean shouldRetainDirectRuntimeOnStop(String transferId, TransferStatus terminalStatus)
    {
        if(resolveTransferMode(transferId) != TransportMode.DIRECT_PEER)
        {
            return false;
        }
        if(terminalStatus != TransferStatus.FAILED && terminalStatus != TransferStatus.CANCELED)
        {
            return false;
        }
        return outboundTransferContexts.containsKey(transferId) || inboundTransferContexts.containsKey(transferId);
    }

    private void retainDirectRetransmissionRuntime(String transferId)
    {
        if(resolveTransferMode(transferId) != TransportMode.DIRECT_PEER)
        {
            return;
        }
        if(!transferTransports.containsKey(transferId)
                && !outboundTransferContexts.containsKey(transferId)
                && !inboundTransferContexts.containsKey(transferId))
        {
            return;
        }
        directRetransmitExpiresAt.put(transferId, Instant.now().plus(DIRECT_RETRANSMIT_RETENTION));
        expiredDirectRetransmissionIds.remove(transferId);
    }

    private void clearDirectRuntimeState(String transferId)
    {
        directRetransmitExpiresAt.remove(transferId);
        pendingRetransmitRequests.remove(transferId);
        acceptedIncomingTransferIds.remove(transferId);
        directReceiverSelectionFutures.remove(transferId);
        OutboundTransferContext outboundContext = outboundTransferContexts.remove(transferId);
        if(outboundContext != null)
        {
            outboundContext.ackFutures.clear();
        }
        InboundTransferContext inboundContext = inboundTransferContexts.remove(transferId);
        if(inboundContext != null)
        {
            inboundContext.closeQuietly();
        }
    }

    private void cleanupExpiredDirectRetransmissionContexts()
    {
        Instant now = Instant.now();
        for(Map.Entry<String, Instant> entry : directRetransmitExpiresAt.entrySet())
        {
            if(entry.getValue().isAfter(now))
            {
                continue;
            }
            String transferId = entry.getKey();
            if(directRetransmitExpiresAt.remove(transferId, entry.getValue()))
            {
                expireDirectRetransmissionRuntime(transferId);
            }
        }
    }

    private void throwIfDirectRetransmissionExpired(String transferId)
    {
        if(isDirectRetransmissionExpired(transferId))
        {
            expireDirectRetransmissionRuntime(transferId);
            throw new IllegalStateException("direct retransmission window expired");
        }
    }

    private boolean isDirectRetransmissionExpired(String transferId)
    {
        Instant expiresAt = directRetransmitExpiresAt.get(transferId);
        return expiresAt != null && !expiresAt.isAfter(Instant.now());
    }

    private void expireDirectRetransmissionRuntime(String transferId)
    {
        closeDirectTransport(transferId);
        clearDirectRuntimeState(transferId);
        expiredDirectRetransmissionIds.add(transferId);
    }

    public void handleIncomingTransferRequest(IncomingTransferRequestPacket packet)
    {
        if(resolveTransferMode(packet.getTransferId()) == TransportMode.DIRECT_PEER || transferTransports.get(packet.getTransferId()) instanceof DirectPeerTransport)
        {
            registerTransferMode(packet.getTransferId(), TransportMode.DIRECT_PEER);
        }
        else
        {
            registerTransferMode(packet.getTransferId(), TransportMode.SERVER_RELAY);
        }
        pendingIncomingTransferRequests.compute(packet.getTransferId(), (transferId, existing) -> existing == null
                ? new PendingIncomingTransferRequest(packet, Instant.now())
                : existing.refresh(packet));
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
        CompletableFuture<ReceiverDeviceSelectionPacket> directFuture = directReceiverSelectionFutures.remove(packet.getTransferId());
        if(directFuture != null)
        {
            directFuture.complete(packet);
            return;
        }
        if (packet.isAccepted()) {
            removePendingIncomingTransferRequest(packet.getTransferId());
            pushNotificationService.publish("incoming-transfer-selected", Map.of(
                    "transferId", packet.getTransferId(),
                    "message", packet.getMessage()
            ));
            return;
        }
        removePendingIncomingTransferRequest(packet.getTransferId());
        pushNotificationService.publish("incoming-transfer-selection-failed", Map.of(
                "transferId", packet.getTransferId(),
                "message", packet.getMessage()
        ));
    }

    public void acceptIncomingTransfer(String transferId)
    {
        PendingIncomingTransferRequest pendingRequest = pendingIncomingTransferRequests.get(transferId);
        IncomingTransferRequestPacket request = pendingRequest == null ? null : pendingRequest.packet();
        if (request == null) {
            throw new IllegalArgumentException("Incoming transfer request not found: " + transferId);
        }
        ensureKnownTransferModeAllowsNetworkControl(transferId);
        sendForTransfer(transferId, new ReceiverDeviceSelectionPacket(transferId, true, "Accepted by receiver device"));
        removePendingIncomingTransferRequest(transferId);
        if(resolveTransferMode(transferId) == TransportMode.DIRECT_PEER)
        {
            acceptedIncomingTransferIds.add(transferId);
        }
    }

    public Map<String, IncomingTransferRequestPacket> pendingIncomingTransferRequests()
    {
        LinkedHashMap<String, IncomingTransferRequestPacket> snapshot = new LinkedHashMap<>();
        pendingIncomingTransferRequestsDetailed().forEach(request -> snapshot.put(request.packet().getTransferId(), request.packet()));
        return snapshot;
    }

    public List<PendingIncomingTransferRequest> pendingIncomingTransferRequestsDetailed()
    {
        return pendingIncomingTransferRequests.values().stream()
                .sorted(Comparator.comparing(PendingIncomingTransferRequest::receivedAt).reversed())
                .toList();
    }

    private void removePendingIncomingTransferRequest(String transferId)
    {
        pendingIncomingTransferRequests.remove(transferId);
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

    public record PendingIncomingTransferRequest(IncomingTransferRequestPacket packet,
                                                 Instant receivedAt)
    {
        private PendingIncomingTransferRequest refresh(IncomingTransferRequestPacket latestPacket)
        {
            return new PendingIncomingTransferRequest(latestPacket, receivedAt);
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

    private record PendingRetransmitRequest(
            RetransmitRequestPacket packet,
            OutboundTransferContext context,
            Path filePath
    )
    {
    }

    //接收文件的时候，用这个来保存接收过程中的临时状态，如AES密钥，输出文件流，传输任务，收到的块，哪些块暂时不能写入，当前是第几块
    private static final class InboundTransferContext
    {
        private final SecretKey secretKey;
        private final OutputStream outputStream;
        private final TransferTask transferTask;//本次接收任务的状态对象
        private final Map<Integer, byte[]> pendingBlocks=new ConcurrentHashMap<>();//暂存已经收到，但还不能马上写入文件的块
        private final Set<Integer> receivedBlockIds=new HashSet<>();//已经接收过的块编号
        private final Queue<Integer> delayedAckBlockIds=new ArrayDeque<>();//缓存压力过高时暂缓回复的ACK
        private long pendingPlainBytes;//已经解密但尚未写入磁盘的明文字节数
        private long receivedBytes;//已经写入文件的字节数
        private int receivedBlocks;//已经写入的块数
        private int nextWriteBlockId;//下一个应该写入的块编号
        private long lastProgressPublishNanos;
        private boolean closed;
        private boolean flushScheduled;

        private InboundTransferContext(SecretKey secretKey, OutputStream outputStream, TransferTask transferTask)
        {
            this.secretKey=secretKey;
            this.outputStream=outputStream;
            this.transferTask=transferTask;
        }

        //接受一个已经解密认证成功的文件块，先放入内存缓存；ACK不再等待磁盘写入。
        private boolean acceptDecryptedBlock(int blockId, byte[] plain)
        {
            if(closed || receivedBlockIds.contains(blockId))
            {
                return false;
            }

            receivedBlockIds.add(blockId);
            pendingBlocks.put(blockId, plain);
            pendingPlainBytes += plain.length;
            return true;
        }

        private boolean shouldDelayAck()
        {
            return pendingPlainBytes >= RECEIVE_PENDING_HIGH_WATERMARK_BYTES;
        }

        private void delayAck(int blockId)
        {
            delayedAckBlockIds.add(blockId);
        }

        private boolean flushContiguousBlocks() throws IOException
        {
            boolean wrote = false;
            while(true)
            {
                //从nextWriteBlockId开始循环取块
                byte[] chunk=pendingBlocks.remove(nextWriteBlockId);

                //如果收到的块号是不连续的那么这里取出来的块就是0
                if(chunk==null)
                {
                    return wrote;
                }
                outputStream.write(chunk);//写入文件
                pendingPlainBytes-=chunk.length;
                receivedBytes+=chunk.length;
                receivedBlocks++;
                nextWriteBlockId++;
                wrote = true;
            }
        }

        private boolean isCompleted()
        {
            //收到块数>=文件总块数说明取完了；receivedBlocks是在后台写文件时增加的。所有块已收到并解密!=任务完成；只有，所有块已按顺序写入文件=任务完成
            return receivedBlocks >= transferTask.getTotalBlocks();
        }

        private boolean shouldReleaseDelayedAcks()
        {
            return pendingPlainBytes <= RECEIVE_PENDING_LOW_WATERMARK_BYTES && !delayedAckBlockIds.isEmpty();
        }

        private List<Integer> drainDelayedAcks()
        {
            List<Integer> blockIds = new ArrayList<>(delayedAckBlockIds);
            delayedAckBlockIds.clear();
            return blockIds;
        }

        private boolean shouldPublishProgress()
        {
            long now = System.nanoTime();
            if(now - lastProgressPublishNanos < RECEIVE_PROGRESS_UPDATE_INTERVAL_NANOS)
            {
                return false;
            }
            lastProgressPublishNanos = now;
            return true;
        }

        private boolean isClosed()
        {
            return closed;
        }

        private void markClosed()
        {
            closed = true;
        }

        private boolean isFlushScheduled()
        {
            return flushScheduled;
        }

        private void markFlushScheduled()
        {
            flushScheduled = true;
        }

        private void clearFlushScheduled()
        {
            flushScheduled = false;
        }

        private boolean hasContiguousPendingBlock()
        {
            return pendingBlocks.containsKey(nextWriteBlockId);
        }

        private void clearBufferedBlocks()
        {
            pendingBlocks.clear();
            delayedAckBlockIds.clear();
            pendingPlainBytes = 0L;
            flushScheduled = false;
        }

        private void closeQuietly()//取消接收任务时需要关闭输出流。
        {
            try
            {
                outputStream.close();
            }
            catch(IOException ignored)
            {
            }
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


/*

接收端现在有一个内存缓存，先把已经解密，但还没写进文件的数据库放进去
设置两条缓存警戒线
256MB, 128MB
正常情况: 收到块-> 解密 -> 放进缓存 -> 马上回ACK
缓存超过256MB时: 收到块-> 解密 -> 放进缓存 -> 先不回ACK（发送方会因为收不到ACK而降低发送的速率）
当缓存降到128MB以下时: 把之前的没回的ACK一次性发回去

*/
