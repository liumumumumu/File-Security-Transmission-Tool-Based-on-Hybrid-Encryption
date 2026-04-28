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
import com.crypto.CryptoSupport;
import com.server.PendingAuthChallenge;
import com.server.ServerClientSession;
import com.server.TransferRoute;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ServerRoutingService
{
    private final CryptoSupport cryptoSupport;
    private final AuthenticationResultProperties authenticationResultProperties;
    private final PushNotificationService pushNotificationService;
    private final RedisStateService redisStateService;
    private final MyBatisPersistenceService myBatisPersistenceService;

    private final Map<String, ServerClientSession> sessionsByDeviceId=new ConcurrentHashMap<>();
    private final Map<Channel, String> deviceIdByChannel=new ConcurrentHashMap<>();


    public ServerRoutingService(AuthenticationResultProperties authenticationResultProperties, CryptoSupport cryptoSupport, PushNotificationService pushNotificationService, RedisStateService redisStateService, MyBatisPersistenceService myBatisPersistenceService) {
        this.authenticationResultProperties = authenticationResultProperties;
        this.cryptoSupport = cryptoSupport;
        this.pushNotificationService = pushNotificationService;
        this.redisStateService = redisStateService;
        this.myBatisPersistenceService = myBatisPersistenceService;
    }

    public void handlePacket(ChannelHandlerContext ctx, Packet packet)
    {
        try
        {
            if(packet instanceof AuthResultPacket authResultPacket)
            {
                handleAuthRequest(ctx.channel(), authResultPacket);
                return;
            }
            if(packet instanceof AuthResponsePacket authResponsePacket)
            {
                handleAuthResponse(ctx.channel(), authResponsePacket);
                return;
            }
            if(packet instanceof PingPacket)
            {
                String deviceId=authenticaedDeviceId(ctx.channel());
                if(deviceId != null)
                {
                    redisStateService.touchOnlineSession(deviceId);
                }
                ctx.writeAndFlush(new PongPacket());
                return;
            }
            String deviceId=authenticaedDeviceId(ctx.channel());
            if(deviceId==null)
            {
                ctx.close();
                return;
            }
            redisStateService.touchOnlineSession(deviceId);

            if(packet instanceof DeviceSelectionPacket deviceSelectionPacket)
            {
                handleDeviceSelection(ctx.channel(), deviceId, deviceSelectionPacket);
                return;
            }
            if(packet instanceof FileOfferPacket fileOfferPacket)
            {
                forwardToReceiver(deviceId, fileOfferPacket.getTransferId(), fileOfferPacket);
                return;
            }
            if(packet instanceof FileAcceptPacket fileAcceptPacket)
            {
                forwardToSender(deviceId, fileAcceptPacket.getTransferId(), fileAcceptPacket);
                return;
            }
            if(packet instanceof FileBlockPacket fileBlockPacket)
            {
                forwardToReceiver(deviceId, fileBlockPacket.getTransferId(), fileBlockPacket);
                return;
            }
            if(packet instanceof AckPacket ackPacket)
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

    public void handleDisconnect(Channel channel)
    {
        String deviceId=deviceIdByChannel.remove(channel);
        if(deviceId!=null)
        {
            return;
        }
        sessionsByDeviceId.remove(deviceId);
        redisStateService.removeOnlineSession(deviceId);
        redisStateService.removeRoutesForDevice(deviceId);
        myBatisPersistenceService.markDeviceOfflineQuietly(deviceId);
        pushNotificationService.publish("server-device-offline", Map.of("deviceId", deviceId));
    }

    private void handleAuthRequest(Channel channel, AuthRequestPacket packet)
    {
        String challengeId= UUID.randomUUID().toString();
        String challenge=UUID.randomUUID().toString();
        redisStateService.saveChallenge(new PendingAuthChallenge(
                packet.getDeviceId(),
                packet.getPublicKey(),
                challengeId,
                challenge
        ));
        channel.writeAndFlush(new ChallengePacket(challenge, challengeId));
    }

    private void handleAuthResponse(Channel channel, AuthResponsePacket packet) throws GeneralSecurityException
    {
        Optional<PendingAuthChallenge> challengeOptional=redisStateService.takeChallenge(packet.getChallengeId());
        PendingAuthChallenge challenge=challengeOptional.orElse(null);
        if(challenge == null)
        {
            myBatisPersistenceService.logAuthFailure(null, packet.getPublicKey(), packet.getChallengeId(), channel, "Challenge not found");
            channel.writeAndFlush(new AuthResultPacket("Challenge not found", false));
            channel.close();
            return;
        }

        boolean verified = cryptoSupport.verifySignature(packet.getPublicKey(), challenge.getChallenge(), packet.getSignature());
        if(!verified)
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

        ServerClientSession existing = sessionsByDeviceId.put(
                challenge.getDeviceId(),
                new ServerClientSession(
                        challenge.getDeviceId(),
                        challenge.getPublicKey(),
                        channel)
        );
        if(existing!=null && existing.getChannel()!=channel)
        {
            existing.getChannel().close();
        }

        deviceIdByChannel.put(channel, challenge.getDeviceId());
        redisStateService.saveOnlineSession(challenge.getDeviceId(), challenge.getPublicKey(), channel.id().asShortText());
        myBatisPersistenceService.upsertOnlineDevice(challenge.getDeviceId(), challenge.getPublicKey());
        myBatisPersistenceService.logAuthSuccess(challenge.getDeviceId(), challenge.getPublicKey(), packet.getChallengeId(), channel);
        channel.writeAndFlush(new AuthResultPacket(authenticationResultProperties.getSucceed(), true));
        pushNotificationService.publish("server-device-online", Map.of(
                "deviceId", challenge.getDeviceId()
        ));

    }

    private void handleDeviceSelection(Channel channel, String senderDeviceId, DeviceSelectionPacket packet)
    {
        ServerClientSession receiver=sessionsByDeviceId.get(packet.getSelectedDeviceId());
        if(receiver==null)
        {
            channel.writeAndFlush(new DeviceSelectionPacket(
                    false,
                    "Target device is offline",
                    packet.getSelectedDeviceId(),
                    packet.getTransferId()
            ));
            return;
        }

        redisStateService.saveTransferRoute(new TransferRoute(
                packet.getTransferId(),
                senderDeviceId,
                receiver.getDeviceId()
        ));

        channel.writeAndFlush(new DeviceSelectionPacket(
                true,
                receiver.getPublicKey(),
                packet.getSelectedDeviceId(),
                packet.getTransferId()
        ));
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
        TransferRoute route=redisStateService.findTransferRoute(transferId);
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
}
