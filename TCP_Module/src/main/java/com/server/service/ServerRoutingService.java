package com.server.service;


import com.common.config.AuthenticationResultProperties;
import com.common.protocol.Packet;
import com.common.protocol.auth.AuthRequestPacket;
import com.common.protocol.auth.AuthResponsePacket;
import com.common.protocol.auth.AuthResultPacket;
import com.common.protocol.auth.ChallengePacket;
import com.common.protocol.file.*;
import com.common.protocol.heartbeat.PingPacket;
import com.common.protocol.heartbeat.PongPacket;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.common.service.PushNotificationService;
import com.crypto.CryptoSupport;
import com.server.PendingAuthChallenge;
import com.server.PendingTransferRequest;
import com.server.ServerClientSession;
import com.server.TransferRoute;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: LQH
 * Date: 2026-04-28
 * Purpose: 服务端中继路由器的核心服务。服务端不负责读写文件，也不负责加密，解密文件，
 * 服务端的业务的主要职责是：
 * 1. 处理客户端认证
 * 2. 维护设备ID->Netty Channel的在线会话
 * 3. 维护文件传输中发送方和接收方的路由关系
 * 4. 把在线状态，认证日志，传输路由写入Redis/ MySQL
 * 5. 推送服务端事件
 * 6. 转发文件传输相关协议和数据包
 *
 * */

@Service
@Slf4j
public class ServerRoutingService
{
    private final CryptoSupport cryptoSupport;
    private final AuthenticationResultProperties authenticationResultProperties;
    private final PushNotificationService pushNotificationService;
    private final RedisStateService redisStateService;
    private final MyBatisPersistenceService myBatisPersistenceService;

    //内存里的在线连接表
    private final Map<String, ServerClientSession> sessionsByDeviceId=new ConcurrentHashMap<>();//根据设备ID找到对应的Netty Channel，从而将包发送给该设备
    private final Map<Channel, String> deviceIdByChannel=new ConcurrentHashMap<>();//根据当前TCP连接反查这个连接属于那个设备
    private final Map<String, Set<String>> deviceIdsByAccountId=new ConcurrentHashMap<>();
    private final Map<String, PendingTransferRequest> pendingTransferRequests = new ConcurrentHashMap<>();
    private final Set<String> forwardedCancelTransferIds = ConcurrentHashMap.newKeySet();
    private final Set<String> forwardedCancelAckTransferIds = ConcurrentHashMap.newKeySet();


    public ServerRoutingService(AuthenticationResultProperties authenticationResultProperties, CryptoSupport cryptoSupport, PushNotificationService pushNotificationService, RedisStateService redisStateService, MyBatisPersistenceService myBatisPersistenceService) {
        this.authenticationResultProperties = authenticationResultProperties;
        this.cryptoSupport = cryptoSupport;
        this.pushNotificationService = pushNotificationService;
        this.redisStateService = redisStateService;
        this.myBatisPersistenceService = myBatisPersistenceService;
    }

    //服务的入口
    public void handlePacket(ChannelHandlerContext ctx, Packet packet)//当Netty Handler收到一个已经解码完成的数据包后进入这个函数
    {
        try
        {
            if(packet instanceof AuthRequestPacket authRequestPacket)//认证请求处理
            {
                handleAuthRequest(ctx.channel(), authRequestPacket);
                return;
            }
            if(packet instanceof AuthResponsePacket authResponsePacket)//认证响应处理
            {
                handleAuthResponse(ctx.channel(), authResponsePacket);
                return;
            }
            if(packet instanceof PingPacket)//心跳包的处理
            {
                String deviceId=authenticaedDeviceId(ctx.channel());
                if(deviceId != null)//该连接已经认证过
                {
                    redisStateService.touchOnlineSession(deviceId);//刷新在线状态
                }
                ctx.writeAndFlush(new PongPacket());
                return;
            }

            //未认证连接禁止传输文件
            String deviceId=authenticaedDeviceId(ctx.channel());
            if(deviceId==null)
            {
                logServerEvent(
                        "unauthenticated-packet",
                        sourcePublicKey(packet, ctx.channel()),
                        "FAILED",
                        "Connection is not authenticated",
                        eventDetails("packetType", packet.getClass().getSimpleName())
                );
                ctx.close();
                return;
            }

            redisStateService.touchOnlineSession(deviceId);//已认证后刷新在线状态

            if (packet instanceof TransferRequestPacket transferRequestPacket) {
                handleTransferRequest(ctx.channel(), deviceId, transferRequestPacket);
                return;
            }
            if (packet instanceof ReceiverDeviceSelectionPacket receiverDeviceSelectionPacket) {
                handleReceiverDeviceSelection(ctx.channel(), deviceId, receiverDeviceSelectionPacket);
                return;
            }
            if(packet instanceof OnlineUserSearchRequestPacket onlineUserSearchRequestPacket)
            {
                handleOnlineUserSearch(ctx.channel(), onlineUserSearchRequestPacket);
                return;
            }
            if(packet instanceof FileOfferPacket fileOfferPacket)//处理文件信息数据包
            {
                forwardToReceiver(deviceId, fileOfferPacket.getTransferId(), fileOfferPacket);
                return;
            }
            if(packet instanceof FileAcceptPacket fileAcceptPacket)//处理文件接收状况包
            {
                forwardToSender(deviceId, fileAcceptPacket.getTransferId(), fileAcceptPacket);
                return;
            }
            if(packet instanceof FileBlockPacket fileBlockPacket)//处理文件块数据包
            {
                forwardToReceiver(deviceId, fileBlockPacket.getTransferId(), fileBlockPacket);
                return;
            }
            if(packet instanceof AckPacket ackPacket)//处理ACK包
            {
                forwardToSender(deviceId, ackPacket.getTransferId(), ackPacket);
                return;
            }
            if(packet instanceof TransferCancelPacket transferCancelPacket)
            {
                handleTransferCancel(deviceId, transferCancelPacket);
                return;
            }
            if(packet instanceof TransferCancelAckPacket transferCancelAckPacket)
            {
                handleTransferCancelAck(deviceId, transferCancelAckPacket);
                return;
            }
            if(packet instanceof RetransmitRequestPacket retransmitRequestPacket)
            {
                forwardToSender(deviceId, retransmitRequestPacket.getTransferId(), retransmitRequestPacket);
                return;
            }
            if(packet instanceof RetransmitAckPacket retransmitAckPacket)
            {
                forwardToReceiver(deviceId, retransmitAckPacket.getTransferId(), retransmitAckPacket);
                return;
            }
        }
        catch(Exception e)
        {
            logServerEvent(
                    "server-packet",
                    sourcePublicKey(packet, ctx.channel()),
                    "FAILED",
                    e.getMessage(),
                    eventDetails("packetType", packet == null ? "" : packet.getClass().getSimpleName())
            );
            pushNotificationService.publish("server-error", Map.of("message", e.getMessage()));
            ctx.close();
        }
    }

    //处理断线
    public void handleDisconnect(Channel channel)//清理函数
    {
        String deviceId=deviceIdByChannel.remove(channel);//根据断开的Channel找到他对应的deviceId, 并把这条映射删掉
        if(deviceId==null)//连接不存在的情况
        {
            return;
        }
        ServerClientSession session = sessionsByDeviceId.get(deviceId);
        if (session != null && session.getChannel() != channel) {
            return;
        }
        if (session != null) {
            removeAccountSessionIndex(session.getAccountId(), deviceId);
        }
        sessionsByDeviceId.remove(deviceId);//清理服务端状态
        redisStateService.removeOnlineSession(deviceId);//删除Redis里面的在线状态
        // 断线不立即删除传输路由。发送/接收端重连后，接收方还需要用原 transferId 发起续传请求。
        // 路由由 Redis TTL、取消确认或后续清理流程回收。
        pendingTransferRequests.entrySet().removeIf(entry -> entry.getValue().getSenderDeviceId().equals(deviceId));
        forwardedCancelTransferIds.removeIf(transferId -> redisStateService.findTransferRoute(transferId)
                .map(route -> route.getSenderDeviceId().equals(deviceId) || route.getReceiverDeviceId().equals(deviceId))
                .orElse(false));
        forwardedCancelAckTransferIds.removeIf(transferId -> redisStateService.findTransferRoute(transferId)
                .map(route -> route.getSenderDeviceId().equals(deviceId) || route.getReceiverDeviceId().equals(deviceId))
                .orElse(false));
        myBatisPersistenceService.markDeviceOfflineQuietly(deviceId);//在MySQL里面标记为离线
        pushNotificationService.publish("server-device-offline", Map.of("deviceId", deviceId));//推送设备离线事件
    }

    //处理认证
    private void handleAuthRequest(Channel channel, AuthRequestPacket packet)//1. 生成ChallengeId, 2. 生成随机Challenge, 3. 把Challenge保存到Redis, 4. 返回Challenge给客户端
    {
        String challengeId= UUID.randomUUID().toString();
        String challenge=UUID.randomUUID().toString();

        //把这个 challenge 保存到 Redis。
        redisStateService.saveChallenge(new PendingAuthChallenge(
                packet.getDeviceId(),
                packet.getPublicKey(),
                challengeId,
                challenge
        ));
        channel.writeAndFlush(new ChallengePacket(challenge, challengeId));//返回 ChallengePacket 给客户端。
        logServerEvent(
                "auth-request",
                packet.getPublicKey(),
                "CHALLENGE_SENT",
                null,
                eventDetails(
                        "deviceId", packet.getDeviceId(),
                        "challengeId", challengeId
                )
        );
    }

    //处理认证结果
    private void handleAuthResponse(Channel channel, AuthResponsePacket packet) throws GeneralSecurityException
    {
        //根据challengeId取出之前保存的Challenge，Optional防空
        Optional<PendingAuthChallenge> challengeOptional=redisStateService.takeChallenge(packet.getChallengeId());//取出来，然后删除，防止Challenge被重复使用
        PendingAuthChallenge challenge=challengeOptional.orElse(null);
        if(challenge == null)//判断Challenge是否存在
        {
            myBatisPersistenceService.logAuthFailure(null, packet.getPublicKey(), packet.getChallengeId(), channel, "Challenge not found");
            channel.writeAndFlush(new AuthResultPacket("Challenge not found", false));//认证失败反馈
            logServerEvent(
                    "auth-response",
                    packet.getPublicKey(),
                    "FAILED",
                    "Challenge not found",
                    eventDetails("challengeId", packet.getChallengeId())
            );
            channel.close();//端口连接
            return;
        }

        // 校验响应公钥必须和发起认证时的公钥一致，避免认证过程被换绑公钥
        if (!challenge.getPublicKey().equals(packet.getPublicKey())) {
            myBatisPersistenceService.logAuthFailure(
                    challenge.getDeviceId(),
                    challenge.getPublicKey(),
                    packet.getChallengeId(),
                    channel,
                    "Public key mismatch"
            );
            channel.writeAndFlush(new AuthResultPacket("Public key mismatch", false));
            logServerEvent(
                    "auth-response",
                    packet.getPublicKey(),
                    "FAILED",
                    "Public key mismatch",
                    eventDetails(
                            "deviceId", challenge.getDeviceId(),
                            "challengeId", packet.getChallengeId(),
                            "expectedPublicKey", challenge.getPublicKey()
                    )
            );
            channel.close();
            return;
        }

//验证签名
        boolean verified = cryptoSupport.verifySignature(challenge.getPublicKey(), challenge.getChallenge(), packet.getSignature());
        if(!verified)//签名验证失败的情况
        {
            myBatisPersistenceService.logAuthFailure(
                    challenge.getDeviceId(),
                    challenge.getPublicKey(),
                    packet.getChallengeId(),
                    channel,
                    authenticationResultProperties.getFailed()
            );
            channel.writeAndFlush(new AuthResultPacket(authenticationResultProperties.getFailed(), false));
            logServerEvent(
                    "auth-response",
                    challenge.getPublicKey(),
                    "FAILED",
                    authenticationResultProperties.getFailed(),
                    eventDetails(
                            "deviceId", challenge.getDeviceId(),
                            "challengeId", packet.getChallengeId()
                    )
            );
            channel.close();
            return;
        }


        //签名验证成功，创建服务端会话
        String accountId=cryptoSupport.publicKeyFingerprint(challenge.getPublicKey());
        ServerClientSession existing = sessionsByDeviceId.put(
                challenge.getDeviceId(),
                new ServerClientSession(
                        accountId,
                        channel,
                        challenge.getDeviceId(),
                        challenge.getPublicKey())
        );
        if(existing!=null && existing.getChannel()!=channel)
        {
            removeAccountSessionIndex(existing.getAccountId(), existing.getDeviceId());
            existing.getChannel().close();//同设备已在线，关闭旧连接
        }

        deviceIdByChannel.put(channel, challenge.getDeviceId());//写入内存在线会话状态
        ServerClientSession current = sessionsByDeviceId.get(challenge.getDeviceId());
        deviceIdsByAccountId.computeIfAbsent(current.getAccountId(), key -> ConcurrentHashMap.newKeySet()).add(current.getDeviceId());
        redisStateService.saveOnlineSession(challenge.getDeviceId(), challenge.getPublicKey(), channel.id().asShortText());//写入Redis在线状态
        myBatisPersistenceService.upsertOnlineDevice(challenge.getDeviceId(), challenge.getPublicKey());//写入MySQL设备状态
        myBatisPersistenceService.logAuthSuccess(challenge.getDeviceId(), challenge.getPublicKey(), packet.getChallengeId(), channel);//记录认证成功日志
        channel.writeAndFlush(new AuthResultPacket(authenticationResultProperties.getSucceed(), true));//将认证结果返回给客户端
        pushNotificationService.publish("server-device-online", Map.of(
                "deviceId", challenge.getDeviceId()
        ));//推送设备上线
        logServerEvent(
                "auth-response",
                challenge.getPublicKey(),
                "SUCCESS",
                null,
                eventDetails(
                        "deviceId", challenge.getDeviceId(),
                        "accountId", accountId,
                        "challengeId", packet.getChallengeId()
                )
        );

    }


    //处理选择接收设备
    private void handleTransferRequest(Channel channel, String senderDeviceId, TransferRequestPacket packet)
    {
        String sourcePublicKey = sourcePublicKey(senderDeviceId);
        Set<String> receiverDeviceIds = deviceIdsByAccountId.get(packet.getTargetAccountId());
        if (receiverDeviceIds == null || receiverDeviceIds.isEmpty())
        {
            String reason = "Target account has no online devices";
            channel.writeAndFlush(new DeviceSelectionPacket(
                    false,
                    reason,
                    packet.getTargetAccountId(),
                    packet.getTransferId()
            ));
            logServerEvent(
                    "file-transfer-request",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "senderDeviceId", senderDeviceId,
                            "targetAccountId", packet.getTargetAccountId(),
                            "transferId", packet.getTransferId(),
                            "fileName", packet.getFileName(),
                            "fileSize", packet.getFileSize(),
                            "totalBlocks", packet.getTotalBlocks()
                    )
            );
            return;
        }

        pendingTransferRequests.put(packet.getTransferId(), new PendingTransferRequest(
                packet.getFileName(),
                packet.getFileSize(),
                senderDeviceId,
                packet.getTargetAccountId(),
                packet.getTotalBlocks(),
                packet.getTransferId()
        ));

        IncomingTransferRequestPacket requestPacket = new IncomingTransferRequestPacket(
                packet.getTransferId(),
                senderDeviceId,
                packet.getTargetAccountId(),
                packet.getFileName(),
                packet.getFileSize(),
                packet.getTotalBlocks()
        );
        for (String receiverDeviceId : receiverDeviceIds) {
            ServerClientSession receiver = sessionsByDeviceId.get(receiverDeviceId);
            if (receiver != null) {
                receiver.getChannel().writeAndFlush(requestPacket);
            }
        }
        logServerEvent(
                "file-transfer-request",
                sourcePublicKey,
                "SUCCESS",
                null,
                eventDetails(
                        "senderDeviceId", senderDeviceId,
                        "targetAccountId", packet.getTargetAccountId(),
                        "receiverDeviceCount", receiverDeviceIds.size(),
                        "transferId", packet.getTransferId(),
                        "fileName", packet.getFileName(),
                        "fileSize", packet.getFileSize(),
                        "totalBlocks", packet.getTotalBlocks()
                )
        );
    }

    private void handleOnlineUserSearch(Channel channel, OnlineUserSearchRequestPacket packet)
    {
        String sourcePublicKey = sourcePublicKey(channel);
        if(packet.getAccountId()==null || packet.getAccountId().isBlank())
        {
            String reason = "accountId is required";
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    reason
            ));
            logServerEvent(
                    "online-user-search",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "requestId", packet.getRequestId(),
                            "targetAccountId", safeValue(packet.getAccountId())
                    )
            );
            return;
        }

        Set<String> deviceIds = deviceIdsByAccountId.get(packet.getAccountId());
        if(deviceIds==null || deviceIds.isEmpty())
        {
            String reason = "Target account has no online devices";
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    reason
            ));
            logServerEvent(
                    "online-user-search",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "requestId", packet.getRequestId(),
                            "targetAccountId", packet.getAccountId()
                    )
            );
            return;
        }

        List<ServerClientSession> onlineSessions = new ArrayList<>();
        for(String deviceId : deviceIds)
        {
            ServerClientSession session = sessionsByDeviceId.get(deviceId);
            if(session!=null)
            {
                onlineSessions.add(session);
            }
        }

        if(onlineSessions.isEmpty())
        {
            String reason = "Target account has no active sessions";
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    reason
            ));
            logServerEvent(
                    "online-user-search",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "requestId", packet.getRequestId(),
                            "targetAccountId", packet.getAccountId()
                    )
            );
            return;
        }

        ServerClientSession firstSession = onlineSessions.get(0);
        channel.writeAndFlush(new OnlineUserSearchResultPacket(
                packet.getAccountId(),
                packet.getRequestId(),
                firstSession.getPublicKey(),
                true,
                "Online user found, onlineDeviceCount="+onlineSessions.size()
        ));
        logServerEvent(
                "online-user-search",
                sourcePublicKey,
                "SUCCESS",
                null,
                eventDetails(
                        "requestId", packet.getRequestId(),
                        "targetAccountId", packet.getAccountId(),
                        "targetPublicKey", firstSession.getPublicKey(),
                        "onlineDeviceCount", onlineSessions.size()
                )
        );
    }

    private void handleReceiverDeviceSelection(Channel channel, String receiverDeviceId, ReceiverDeviceSelectionPacket packet)
    {
        String sourcePublicKey = sourcePublicKey(receiverDeviceId);
        PendingTransferRequest request = pendingTransferRequests.get(packet.getTransferId());
        if (request == null) {
            String reason = "Transfer request not found";
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, reason));
            logServerEvent(
                    "receiver-device-selection",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "receiverDeviceId", receiverDeviceId,
                            "transferId", packet.getTransferId(),
                            "accepted", packet.isAccepted()
                    )
            );
            return;
        }

        ServerClientSession receiver = sessionsByDeviceId.get(receiverDeviceId);
        if (receiver == null || !receiver.getAccountId().equals(request.getTargetAccountId())) {
            String reason = "Receiver device is not in target account";
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, reason));
            logServerEvent(
                    "receiver-device-selection",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "receiverDeviceId", receiverDeviceId,
                            "targetAccountId", request.getTargetAccountId(),
                            "transferId", packet.getTransferId(),
                            "accepted", packet.isAccepted()
                    )
            );
            return;
        }

        ServerClientSession sender = sessionsByDeviceId.get(request.getSenderDeviceId());
        if (sender == null) {
            pendingTransferRequests.remove(packet.getTransferId());
            String reason = "Sender is offline";
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, reason));
            logServerEvent(
                    "receiver-device-selection",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "receiverDeviceId", receiverDeviceId,
                            "senderDeviceId", request.getSenderDeviceId(),
                            "transferId", packet.getTransferId(),
                            "accepted", packet.isAccepted()
                    )
            );
            return;
        }

        if (!packet.isAccepted()) {
            PendingTransferRequest canceled = pendingTransferRequests.remove(packet.getTransferId());
            if(canceled != null)
            {
                sender.getChannel().writeAndFlush(new DeviceSelectionPacket(
                        false,
                        "Transfer canceled by receiver device: "+receiver.getDeviceId(),
                        receiver.getDeviceId(),
                        canceled.getTransferId()
                ));
                notifyReceiverDevices(
                        canceled.getTargetAccountId(),
                        canceled.getTransferId(),
                        false,
                        "Transfer request canceled by receiver device: "+receiver.getDeviceId()
                );
            }
            logServerEvent(
                    "receiver-device-selection",
                    sourcePublicKey,
                    "REJECTED",
                    packet.getMessage() == null || packet.getMessage().isBlank() ? "Receiver rejected transfer" : packet.getMessage(),
                    eventDetails(
                            "receiverDeviceId", receiverDeviceId,
                            "senderDeviceId", request.getSenderDeviceId(),
                            "transferId", packet.getTransferId(),
                            "accepted", false
                    )
            );
            return;
        }

        PendingTransferRequest selected = pendingTransferRequests.remove(packet.getTransferId());
        if (selected == null) {
            String reason = "Transfer request already selected";
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, reason));
            logServerEvent(
                    "receiver-device-selection",
                    sourcePublicKey,
                    "FAILED",
                    reason,
                    eventDetails(
                            "receiverDeviceId", receiverDeviceId,
                            "transferId", packet.getTransferId(),
                            "accepted", packet.isAccepted()
                    )
            );
            return;
        }

        redisStateService.saveTransferRoute(new TransferRoute(
                selected.getSenderDeviceId(),
                selected.getTransferId(),
                receiver.getDeviceId()
        ));

        notifyUnselectedReceiverDevices(selected.getTargetAccountId(), receiver.getDeviceId(), selected.getTransferId());
        sender.getChannel().writeAndFlush(new DeviceSelectionPacket(
                true,
                receiver.getPublicKey(),
                receiver.getDeviceId(),
                selected.getTransferId()
        ));
        channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), true, "Receiver device selected"));
        logServerEvent(
                "receiver-device-selection",
                sourcePublicKey,
                "SUCCESS",
                null,
                eventDetails(
                        "receiverDeviceId", receiverDeviceId,
                        "senderDeviceId", selected.getSenderDeviceId(),
                        "transferId", selected.getTransferId(),
                        "accepted", true
                )
        );
    }

    private void removeAccountSessionIndex(String accountId, String deviceId)
    {
        Set<String> deviceIds = deviceIdsByAccountId.get(accountId);
        if (deviceIds == null) {
            return;
        }
        deviceIds.remove(deviceId);
        if (deviceIds.isEmpty()) {
            deviceIdsByAccountId.remove(accountId, deviceIds);
        }
    }

    private void notifyUnselectedReceiverDevices(String accountId, String selectedDeviceId, String transferId)
    {
        Set<String> deviceIds = deviceIdsByAccountId.get(accountId);
        if (deviceIds == null) {
            return;
        }
        for (String deviceId : deviceIds) {
            if (deviceId.equals(selectedDeviceId)) {
                continue;
            }
            ServerClientSession session = sessionsByDeviceId.get(deviceId);
            if (session != null) {
                session.getChannel().writeAndFlush(new ReceiverDeviceSelectionPacket(
                        transferId,
                        false,
                        "Transfer request selected by another device"
                ));
            }
        }
    }

    //服务端把发送方发来的数据包，转发被对应的接收方
    private void forwardToReceiver(String deviceId, String transferId, Packet packet)
    {
        TransferRoute route=redisStateService.findTransferRoute(transferId).orElse(null);//根据transferId从Redis里查传输路由；senderDeviceId, receiverDeviceId
        if(route==null || !route.getSenderDeviceId().equals(deviceId))//校验参数，同时也要防止其他设备伪造发送方
        {
            throw new IllegalStateException("Transfer route not find for sender and transferId="+transferId);
        }

        ServerClientSession receiver=sessionsByDeviceId.get(route.getReceiverDeviceId());
        if(receiver==null)
        {
            throw new IllegalStateException("Receiver is offline"+route.getReceiverDeviceId());
        }
        receiver.getChannel().writeAndFlush(packet);
    }

    //服务端转发函数，接收方发来某个和传输相关的包是，服务端根据transferId找到对应的发送方，然后把这个包转发给发送方
    private void forwardToSender(String receiverDeviceId, String transferId, Packet packet)
    {
        TransferRoute route=redisStateService.findTransferRoute(transferId).orElse(null);//先从Redis里查这条传输路由的信息；transferId, senderDeviceId, receiverDeviceId
        if(route==null || !route.getReceiverDeviceId().equals(receiverDeviceId))//校验参数
        {
            throw new IllegalStateException("TransferRoute not found for receiver and transferId="+transferId);//找不到route就抛出异常
        }

        ServerClientSession sender=sessionsByDeviceId.get(route.getSenderDeviceId());//通过senderDeviceId,从在线会话表里找发送方连接
        if(sender==null)
        {
            throw new IllegalStateException("Sender is offline="+route.getSenderDeviceId());//发送方不在线，就抛出异常
        }
        sender.getChannel().writeAndFlush(packet);//发送方在线，就通过它的Netty Channel把packet写进去
    }

    //服务端处理客户端发来的取消传输请求的函数；
    private void handleTransferCancel(String deviceId, TransferCancelPacket packet)     //1.判断这个取消请求来自发送方还是接收方；2.将TransferCancelPacket转发给传输的另一端；3.并清理服务端保存的传输路由；4.传输还没正式建立，则取消待处理的传输请求；5.记录取消成功或失败日志
    {
        TransferRoute route = redisStateService.findTransferRoute(packet.getTransferId()).orElse(null);//检查传输路由
        if (route != null) {
            boolean firstCancelForward = forwardedCancelTransferIds.add(packet.getTransferId());
            if (route.getSenderDeviceId().equals(deviceId)) {   //如果是发送方主动发起的取消
                ServerClientSession receiver = sessionsByDeviceId.get(route.getReceiverDeviceId());//如果当前发包设备就是发送方，就找接收方连接
                if (receiver != null) {
                    writeAndFlushWithListener(      //服务端把取消包转发给接收方
                            receiver,
                            packet,
                            "transfer-cancel-forward",
                            sourcePublicKey(deviceId),
                            eventDetails(       //时间信息
                                    "transferId", packet.getTransferId(),
                                    "fromDeviceId", deviceId,
                                    "toDeviceId", route.getReceiverDeviceId(),
                                    "cancelRole", "sender"
                            ),
                            () -> {     //如果发送方取消成功转发给接收方，服务端会删除这条传输路由
                                redisStateService.removeTransferRoute(packet.getTransferId());
                                logTransferCancel(deviceId, packet, firstCancelForward ? "sender" : "duplicate-sender");
                            },
                            () -> forwardedCancelTransferIds.remove(packet.getTransferId())
                    );
                    return;
                }
                forwardedCancelTransferIds.remove(packet.getTransferId());      //如果接收方不在线，服务端移除去重标记并记录失败日志
                logTransferCancelFailure(deviceId, packet.getTransferId(), "Receiver is offline="+route.getReceiverDeviceId(), "sender");
                return;
            }

            if (route.getReceiverDeviceId().equals(deviceId)) {     //如果是接收方发起的
                ServerClientSession sender = sessionsByDeviceId.get(route.getSenderDeviceId()); //如果发送方在线，就把取消包转发给发送方
                if (sender != null) {
                    writeAndFlushWithListener(
                            sender,
                            packet,
                            "transfer-cancel-forward",
                            sourcePublicKey(deviceId),
                            eventDetails(
                                    "transferId", packet.getTransferId(),
                                    "fromDeviceId", deviceId,
                                    "toDeviceId", route.getSenderDeviceId(),
                                    "cancelRole", "receiver"
                            ),
                            () -> logTransferCancel(deviceId, packet, firstCancelForward ? "receiver" : "duplicate-receiver"),      //记录日志
                            () -> forwardedCancelTransferIds.remove(packet.getTransferId())
                    );
                    return;
                }
                forwardedCancelTransferIds.remove(packet.getTransferId());
                logTransferCancelFailure(deviceId, packet.getTransferId(), "Sender is offline="+route.getSenderDeviceId(), "receiver");//发送方不在线就记录失败
                return;
            }
            forwardedCancelTransferIds.remove(packet.getTransferId());//发起设备不是该传输的发送方或接收方，拒绝取消请求
            String reason = "Cancel requester is not a participant of transferId="+packet.getTransferId();
            logTransferCancelFailure(deviceId, packet.getTransferId(), reason, "unauthorized");
            sendTransferCancelReject(deviceId, packet.getTransferId(), reason);
            return;
        }

        PendingTransferRequest pending = pendingTransferRequests.remove(packet.getTransferId());
        if (pending != null && pending.getSenderDeviceId().equals(deviceId)) {
            notifyReceiverDevices(
                    pending.getTargetAccountId(),
                    pending.getTransferId(),
                    false,
                    packet.getReason() == null || packet.getReason().isBlank() ? "Transfer canceled by sender" : packet.getReason()
            );
            logTransferCancel(deviceId, packet, "pending-sender");
        }
    }

    private void handleTransferCancelAck(String deviceId, TransferCancelAckPacket packet)       //服务端处理取消确认ACK数据包的函数，验证是否来自发送方，再转发给接收方，最后清理传输路由并记录日志
    {
        TransferRoute route = redisStateService.findTransferRoute(packet.getTransferId()).orElse(null);//根据transferId查传输路由
        if(route == null)//route不存在
        {
            logTransferCancelAck(deviceId, packet, "duplicate-or-missing-route");//记录日志然后返回
            return;
        }
        if(!route.getSenderDeviceId().equals(deviceId))
        {
            logTransferCancelAckFailure(deviceId, packet.getTransferId(), "Cancel ACK must be sent by sender device");
            return;
        }
        boolean firstAckForward = forwardedCancelAckTransferIds.add(packet.getTransferId());//如果不是发送方发来的ACK就拒绝处理，并记录失败日志

        ServerClientSession receiver = sessionsByDeviceId.get(route.getReceiverDeviceId());//找接收方连接
        if(receiver == null)//接收方不在线就移除ACK转发去重标记，记录失败日志
        {
            forwardedCancelAckTransferIds.remove(packet.getTransferId());
            logTransferCancelAckFailure(deviceId, packet.getTransferId(), "Receiver is offline="+route.getReceiverDeviceId());
            return;
        }

        writeAndFlushWithListener(      //如果接收方在线，服务端就把TransferCancelAckPacket转发给接收方
                receiver,
                packet,
                "transfer-cancel-ack-forward",
                sourcePublicKey(deviceId),
                eventDetails(   //事件详情
                        "transferId", packet.getTransferId(),
                        "fromDeviceId", deviceId,
                        "toDeviceId", route.getReceiverDeviceId(),
                        "ackByDeviceId", packet.getAckByDeviceId(),
                        "status", safeValue(packet.getStatus())
                ),
                () -> {
                    redisStateService.removeTransferRoute(packet.getTransferId());//删除Redis里的传输路由
                    forwardedCancelTransferIds.remove(packet.getTransferId());//取消转发标记
                    logTransferCancelAck(deviceId, packet, firstAckForward ? "forwarded" : "duplicate-forwarded");//记录ACK转发日志
                },
                () -> forwardedCancelAckTransferIds.remove(packet.getTransferId())
        );
    }

    //服务端拒绝传输取消请求的辅助函数
    private void sendTransferCancelReject(String deviceId, String transferId, String reason)//防止非法取消
    {
        ServerClientSession requester = sessionsByDeviceId.get(deviceId);//根据deviceId找到这个请求设备当前的在线会话
        if(requester == null)   //设备不在线或者设备不存在
        {
            logTransferCancelFailure(deviceId, transferId, "Cancel requester is offline; reject response not sent", "unauthorized");//取消失败
            return;
        }

        //如果请求设备在线且合法就发一个TransferCancelAckPacket回去
        writeAndFlushWithListener(
                requester,
                new TransferCancelAckPacket(transferId, "server", "REJECTED", reason),
                "transfer-cancel-reject",
                sourcePublicKey(deviceId),
                eventDetails(
                        "deviceId", deviceId,
                        "transferId", transferId,
                        "status", "REJECTED",
                        "message", reason
                ),
                null,
                null
        );
    }

    //记录取消事件
    private void logTransferCancel(String deviceId, TransferCancelPacket packet, String cancelRole)
    {
        logServerEvent(
                "transfer-cancel",
                sourcePublicKey(deviceId),
                "CANCELED",
                null,
                eventDetails(
                        "deviceId", deviceId,
                        "transferId", packet.getTransferId(),
                        "cancelRole", cancelRole,
                        "reason", safeValue(packet.getReason())
                )
        );
    }

    private void logTransferCancelFailure(String deviceId, String transferId, String reason, String cancelRole)
    {
        logServerEvent(
                "transfer-cancel",
                sourcePublicKey(deviceId),
                "FAILED",
                reason,
                eventDetails(
                        "deviceId", deviceId,
                        "transferId", transferId,
                        "cancelRole", cancelRole
                )
        );
    }

    private void logTransferCancelAck(String deviceId, TransferCancelAckPacket packet, String result)
    {
        logServerEvent(
                "transfer-cancel-ack",
                sourcePublicKey(deviceId),
                result,
                null,
                eventDetails(
                        "deviceId", deviceId,
                        "transferId", packet.getTransferId(),
                        "ackByDeviceId", packet.getAckByDeviceId(),
                        "status", safeValue(packet.getStatus()),
                        "message", safeValue(packet.getMessage())
                )
        );
    }

    private void logTransferCancelAckFailure(String deviceId, String transferId, String reason)
    {
        logServerEvent(
                "transfer-cancel-ack",
                sourcePublicKey(deviceId),
                "FAILED",
                reason,
                eventDetails(
                        "deviceId", deviceId,
                        "transferId", transferId
                )
        );
    }

    private void writeAndFlushWithListener(
            ServerClientSession session,
            Packet packet,
            String event,
            String sourcePublicKey,
            Map<String, ?> details,
            Runnable onSuccess,
            Runnable onFailure
    )
    {
        ChannelFuture future = session.getChannel().writeAndFlush(packet);
        future.addListener(writeFuture -> {
            if(writeFuture.isSuccess())
            {
                if(onSuccess != null)
                {
                    onSuccess.run();
                }
                return;
            }
            String message = writeFuture.cause() == null ? "writeAndFlush failed" : writeFuture.cause().getMessage();
            logServerEvent(event, sourcePublicKey, "FAILED", message, details);
            pushNotificationService.publish("server-error", Map.of("message", message));
            if(onFailure != null)
            {
                onFailure.run();
            }
        });
    }

    private String authenticaedDeviceId(Channel channel)
    {
        return deviceIdByChannel.get(channel);
    }

    private String sourcePublicKey(Channel channel)
    {
        String deviceId = authenticaedDeviceId(channel);
        return sourcePublicKey(deviceId);
    }

    private String sourcePublicKey(Packet packet, Channel channel)
    {
        if (packet instanceof AuthRequestPacket authRequestPacket) {
            return authRequestPacket.getPublicKey();
        }
        if (packet instanceof AuthResponsePacket authResponsePacket) {
            return authResponsePacket.getPublicKey();
        }
        return sourcePublicKey(channel);
    }

    private String sourcePublicKey(String deviceId)
    {
        if (deviceId == null) {
            return null;
        }
        ServerClientSession session = sessionsByDeviceId.get(deviceId);
        return session == null ? null : session.getPublicKey();
    }

    private void logServerEvent(String event, String sourcePublicKey, String result, String failureReason, Map<String, ?> details)
    {
        Map<String, ?> safeDetails = details == null ? Map.of() : details;
        if (failureReason == null || failureReason.isBlank()) {
            log.info(
                    "server-event event={} time={} sourcePublicKey={} result={} details={}",
                    event,
                    Instant.now(),
                    safeValue(sourcePublicKey),
                    result,
                    safeDetails
            );
            return;
        }

        log.warn(
                "server-event event={} time={} sourcePublicKey={} result={} failureReason={} details={}",
                event,
                Instant.now(),
                safeValue(sourcePublicKey),
                result,
                failureReason,
                safeDetails
        );
    }

    private String safeValue(String value)
    {
        return value == null ? "" : value;
    }

    private Map<String, Object> eventDetails(Object... keyValues)
    {
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            details.put(String.valueOf(keyValues[i]), keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return details;
    }

    private void notifyReceiverDevices(String accountId, String transferId, boolean accepted, String message)
    {
        Set<String> deviceIds=deviceIdsByAccountId.get(accountId);
        if(deviceIds==null)
        {
            return;
        }
        for(String deviceId : deviceIds)
        {
            ServerClientSession session=sessionsByDeviceId.get(deviceId);
            if(session!=null)
            {
                session.getChannel().writeAndFlush(new ReceiverDeviceSelectionPacket(
                   transferId,
                   accepted,
                   message
                ));
            }
        }
    }
}

/*

对于非法传输的防护措施


1. 未认证连接不能发传输包
2. 认证必须证明持有私钥
3. accountId 由公钥指纹计算，绑定到会话
4. 发送请求只能发给在线目标 accountId
5. 接收选择必须来自目标 accountId 下的设备
6. 服务端保存 transferId -> senderDeviceId + receiverDeviceId
7. 后续每个传输包都按 route 校验发送角色
8. 客户端只处理本地存在 transferId 上下文的文件块
9. AES-GCM 校验密文完整性，篡改或伪造会解密失败



 */
