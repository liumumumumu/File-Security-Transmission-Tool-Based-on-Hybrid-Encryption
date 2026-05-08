package com.handler;

import com.client.ClientConnectionManager;
import com.common.protocol.Packet;
import com.common.protocol.auth.AuthResultPacket;
import com.common.protocol.auth.ChallengePacket;
import com.common.protocol.file.*;
import com.common.protocol.heartbeat.PingPacket;
import com.common.protocol.heartbeat.PongPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.service.ClientTransferService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-05-02
 * Purpose: Netty管道里业务包分发器（客户端）
 * 根据包的具体类型，转交给对应的服务类处理
 *
 * */

@Component
@ChannelHandler.Sharable
public class ClientPacketHandler extends SimpleChannelInboundHandler<Packet>
{
    private final ClientConnectionManager clientConnectionManager;
    private final ClientTransferService clientTransferService;

    public ClientPacketHandler(@Lazy ClientConnectionManager clientConnectionManager, @Lazy ClientTransferService clientTransferService)//延迟加载注解，不要在容器启动时立刻创建这个依赖对象，而是在真正用到它的时候再创建。
    {
        this.clientConnectionManager = clientConnectionManager;
        this.clientTransferService = clientTransferService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg)throws Exception
    {
        if(msg instanceof ChallengePacket challengePacket)
        {
            clientConnectionManager.handleChallenge(challengePacket);
            return;
        }
        if(msg instanceof AuthResultPacket authResultPacket)
        {
            clientConnectionManager.handleAuthResult(authResultPacket);
            return;
        }
        if(msg instanceof DeviceSelectionPacket deviceSelectionPacket)
        {
            clientTransferService.handleDeviceSelection(deviceSelectionPacket);
            return;
        }
        if(msg instanceof FileAcceptPacket fileAcceptPacket)
        {
            clientTransferService.handleFileAccept(fileAcceptPacket);
            return;
        }
        if(msg instanceof AckPacket ackPacket)
        {
            clientTransferService.handleAck(ackPacket);
            return;
        }
        if(msg instanceof FileOfferPacket fileOfferPacket)
        {
            clientTransferService.handleIncomingOffer(fileOfferPacket);
            return;
        }
        if(msg instanceof IncomingTransferRequestPacket incomingTransferRequestPacket)
        {
            clientTransferService.handleIncomingTransferRequest(incomingTransferRequestPacket);
            return;
        }
        if(msg instanceof ReceiverDeviceSelectionPacket receiverDeviceSelectionPacket)
        {
            clientTransferService.handleReceiverDeviceSelection(receiverDeviceSelectionPacket);
            return;
        }
        if(msg instanceof OnlineUserSearchResultPacket onlineUserSearchResultPacket)
        {
            clientTransferService.handleOnlineUserSearchResult(onlineUserSearchResultPacket);
            return;
        }
        if(msg instanceof FileBlockPacket fileBlockPacket)
        {
            clientTransferService.handleIncommingBlock(fileBlockPacket);
            return;
        }
        if(msg instanceof PingPacket pingPacket)
        {
            ctx.writeAndFlush(new PongPacket());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)throws Exception
    {
        clientConnectionManager.handleChannelInactive();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)throws Exception
    {
        clientConnectionManager.handleChannelInactive();
        ctx.close();
    }
}
