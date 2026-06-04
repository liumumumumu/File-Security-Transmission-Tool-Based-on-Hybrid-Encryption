package com.client.direct;

import com.client.message.ClientMessageService;
import com.client.service.ClientTransferService;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionAcceptedPacket;
import com.common.protocol.direct.DirectSessionChallengePacket;
import com.common.protocol.direct.DirectSessionHelloPacket;
import com.common.protocol.direct.DirectSessionProofPacket;
import com.common.protocol.file.AckPacket;
import com.common.protocol.file.FileAcceptPacket;
import com.common.protocol.file.FileBlockPacket;
import com.common.protocol.file.FileOfferPacket;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.file.ReceiverDeviceSelectionPacket;
import com.common.protocol.file.RetransmitAckPacket;
import com.common.protocol.file.RetransmitRequestPacket;
import com.common.protocol.file.TransferCancelAckPacket;
import com.common.protocol.file.TransferCancelPacket;
import com.common.protocol.message.TextMessageAckPacket;
import com.common.protocol.message.TextMessagePacket;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class DirectPeerPacketHandler extends SimpleChannelInboundHandler<Packet>
{
    private final DirectPeerConnectionManager directPeerConnectionManager;
    private final ClientTransferService clientTransferService;
    private final ClientMessageService clientMessageService;

    public DirectPeerPacketHandler(@Lazy DirectPeerConnectionManager directPeerConnectionManager,
                                   @Lazy ClientTransferService clientTransferService,
                                   @Lazy ClientMessageService clientMessageService)
    {
        this.directPeerConnectionManager = directPeerConnectionManager;
        this.clientTransferService = clientTransferService;
        this.clientMessageService = clientMessageService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception
    {
        if(msg instanceof DirectSessionHelloPacket packet)
        {
            directPeerConnectionManager.handleHello(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionChallengePacket packet)
        {
            directPeerConnectionManager.handleChallenge(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionProofPacket packet)
        {
            directPeerConnectionManager.handleProof(ctx.channel(), packet);
            return;
        }
        if(msg instanceof DirectSessionAcceptedPacket packet)
        {
            directPeerConnectionManager.handleAccepted(ctx.channel(), packet);
            return;
        }

        String transferId = transferId(msg);
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

    @Override
    public void channelInactive(ChannelHandlerContext ctx)
    {
        directPeerConnectionManager.handleChannelInactive(ctx.channel());
    }

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
