package com.service;


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
import com.crypto.CryptoSupport;
import com.server.PendingAuthChallenge;
import com.server.PendingTransferRequest;
import com.server.ServerClientSession;
import com.server.TransferRoute;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
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
        }
        catch(Exception e)
        {
            log.info("server-error: "+e.getMessage());
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
        redisStateService.removeRoutesForDevice(deviceId);//删除和该设备有关的传输路由
        pendingTransferRequests.entrySet().removeIf(entry -> entry.getValue().getSenderDeviceId().equals(deviceId));
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

    }


    //处理选择接收设备
    private void handleTransferRequest(Channel channel, String senderDeviceId, TransferRequestPacket packet)
    {
        Set<String> receiverDeviceIds = deviceIdsByAccountId.get(packet.getTargetAccountId());
        if (receiverDeviceIds == null || receiverDeviceIds.isEmpty())
        {
            channel.writeAndFlush(new DeviceSelectionPacket(
                    false,
                    "Target account has no online devices",
                    packet.getTargetAccountId(),
                    packet.getTransferId()
            ));
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
    }

    private void handleOnlineUserSearch(Channel channel, OnlineUserSearchRequestPacket packet)
    {
        if(packet.getAccountId()==null || packet.getAccountId().isBlank())
        {
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    "accountId is required"
            ));
            return;
        }

        Set<String> deviceIds = deviceIdsByAccountId.get(packet.getAccountId());
        if(deviceIds==null || deviceIds.isEmpty())
        {
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    "Target account has no online devices"
            ));
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
            channel.writeAndFlush(new OnlineUserSearchResultPacket(
                    packet.getAccountId(),
                    packet.getRequestId(),
                    null,
                    false,
                    "Target account has no active sessions"
            ));
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
    }

    private void handleReceiverDeviceSelection(Channel channel, String receiverDeviceId, ReceiverDeviceSelectionPacket packet)
    {
        PendingTransferRequest request = pendingTransferRequests.get(packet.getTransferId());
        if (request == null) {
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, "Transfer request not found"));
            return;
        }

        ServerClientSession receiver = sessionsByDeviceId.get(receiverDeviceId);
        if (receiver == null || !receiver.getAccountId().equals(request.getTargetAccountId())) {
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, "Receiver device is not in target account"));
            return;
        }

        ServerClientSession sender = sessionsByDeviceId.get(request.getSenderDeviceId());
        if (sender == null) {
            pendingTransferRequests.remove(packet.getTransferId());
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, "Sender is offline"));
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
            return;
        }

        PendingTransferRequest selected = pendingTransferRequests.remove(packet.getTransferId());
        if (selected == null) {
            channel.writeAndFlush(new ReceiverDeviceSelectionPacket(packet.getTransferId(), false, "Transfer request already selected"));
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

    private void forwardToReceiver(String deviceId, String transferId, Packet packet)
    {
        TransferRoute route=redisStateService.findTransferRoute(transferId).orElse(null);
        if(route==null || !route.getSenderDeviceId().equals(deviceId))
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

    private void forwardToSender(String receiverDeviceId, String transferId, Packet packet)
    {
        TransferRoute route=redisStateService.findTransferRoute(transferId).orElse(null);
        if(route==null || !route.getReceiverDeviceId().equals(receiverDeviceId))
        {
            throw new IllegalStateException("TransferRoute not found for receiver and transferId="+transferId);
        }

        ServerClientSession sender=sessionsByDeviceId.get(route.getSenderDeviceId());
        if(sender==null)
        {
            throw new IllegalStateException("Sender is offline="+route.getSenderDeviceId());
        }
        sender.getChannel().writeAndFlush(packet);
    }

    private String authenticaedDeviceId(Channel channel)
    {
        return deviceIdByChannel.get(channel);
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
