package com.client.direct;

import com.client.message.ClientMessageService;
import com.client.service.ClientTransferService;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionAcceptedPacket;
import com.common.protocol.direct.DirectSessionChallengePacket;
import com.common.protocol.direct.DirectSessionHelloPacket;
import com.common.protocol.direct.DirectSessionProofPacket;
import com.common.protocol.file.*;
import com.common.protocol.message.TextMessageAckPacket;
import com.common.protocol.message.TextMessagePacket;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-05-20
 * Purpose: 真正处理业务数据包的入站Handler
 *
 * */

@Component
@ChannelHandler.Sharable
public class DirectPeerPacketHandler extends SimpleChannelInboundHandler<Packet> //SimpleChannelInboundHandler只处理入站的Packet类型消息
{
    //Handler本身不直接处理业务，但是会把消息分发给其他的服务
    private final DirectPeerConnectionManager directPeerConnectionManager;//负责管理直连会话；握手，认证，连接状态维护
    private final ClientTransferService clientTransferService;//负责文件传输业务；文件请求，文件块，确认包，取消传输，重传
    private final ClientMessageService clientMessageService;

    public DirectPeerPacketHandler(@Lazy DirectPeerConnectionManager directPeerConnectionManager, //延迟注入，避免循环依赖
                                   @Lazy ClientTransferService clientTransferService,
                                   @Lazy ClientMessageService clientMessageService)
    {
        this.directPeerConnectionManager = directPeerConnectionManager;
        this.clientTransferService = clientTransferService;
        this.clientMessageService = clientMessageService;
    }

    //消息分发
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception
    {
        if(msg instanceof DirectSessionHelloPacket packet)//处理直连会话握手包
        {
            directPeerConnectionManager.handleHello(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionChallengePacket packet)//直连会话建立认证包
        {
            directPeerConnectionManager.handleChallenge(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionProofPacket packet)//接收方发给发送方的挑战包
        {
            directPeerConnectionManager.handleProof(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionAcceptedPacket packet)//发送方对challenge的证明包
        {
            directPeerConnectionManager.handleAccepted(ctx.channel(), packet);
            return;
        }

        String transferId = transferId(msg);//注册直连传输任务
        if(transferId != null)
        {
            directPeerConnectionManager.transportFor(ctx.channel()).ifPresent(transport ->
                    clientTransferService.registerDirectTransfer(transferId, transport));
        }

        if(msg instanceof IncomingTransferRequestPacket packet)
        {
            clientTransferService.handleIncomingTransferRequest(packet);
            return;
        }
        if(msg instanceof TextMessagePacket packet)
        {
            directPeerConnectionManager.transportFor(ctx.channel()).ifPresent(transport ->
                    clientMessageService.handleIncomingDirect(packet, transport));
            return;
        }
        if(msg instanceof TextMessageAckPacket packet)
        {
            clientMessageService.handleAck(packet);
            return;
        }
        if(msg instanceof TextMessageReadReceiptPacket packet)
        {
            clientMessageService.handleReadReceipt(packet);
            return;
        }
        if(msg instanceof ReceiverDeviceSelectionPacket packet)
        {
            clientTransferService.handleReceiverDeviceSelection(packet);
            return;
        }
        if(msg instanceof FileOfferPacket packet)
        {
            clientTransferService.handleIncomingOffer(packet);
            return;
        }
        if(msg instanceof FileAcceptPacket packet)
        {
            clientTransferService.handleFileAccept(packet);
            return;
        }
        if(msg instanceof FileBlockPacket packet)
        {
            clientTransferService.handleIncommingBlock(packet);
            return;
        }
        if(msg instanceof AckPacket packet)
        {
            clientTransferService.handleAck(packet);
            return;
        }
        if(msg instanceof TransferCancelPacket packet)
        {
            clientTransferService.handleTransferCancel(packet);
            return;
        }
        if(msg instanceof TransferCancelAckPacket packet)
        {
            clientTransferService.handleTransferCancelAck(packet);
            return;
        }
        if(msg instanceof RetransmitRequestPacket packet)
        {
            clientTransferService.handleRetransmitRequest(packet);
            return;
        }
        if(msg instanceof RetransmitAckPacket packet)
        {
            clientTransferService.handleRetransmitAck(packet);
        }
    }

    //处理连接断开的函数，由Netty负责断开
    @Override
    public void channelInactive(ChannelHandlerContext ctx)
    {
        directPeerConnectionManager.handleChannelInactive(ctx.channel());
    }

    //从不同类型的传输包里提取传输任务Id
    private String transferId(Packet packet)
    {
        if(packet instanceof IncomingTransferRequestPacket value) return value.getTransferId();
        if(packet instanceof ReceiverDeviceSelectionPacket value) return value.getTransferId();
        if(packet instanceof FileOfferPacket value) return value.getTransferId();
        if(packet instanceof FileAcceptPacket value) return value.getTransferId();
        if(packet instanceof FileBlockPacket value) return value.getTransferId();
        if(packet instanceof AckPacket value) return value.getTransferId();
        if(packet instanceof TransferCancelPacket value) return value.getTransferId();
        if(packet instanceof TransferCancelAckPacket value) return value.getTransferId();
        if(packet instanceof RetransmitRequestPacket value) return value.getTransferId();
        if(packet instanceof RetransmitAckPacket value) return value.getTransferId();
        return null;
    }
}
