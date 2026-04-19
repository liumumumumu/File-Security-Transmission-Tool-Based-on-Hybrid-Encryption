package com.client;

import com.common.codec.netty.PacketByteBufDecoder;
import com.common.codec.netty.PacketByteBufEncoder;
import com.common.protocol.Packet;
import com.handler.ClientPacketHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;

public class ClientChannelInitializer extends ChannelInitializer<SocketChannel>
{
    private final ClientPacketHandler clientPacketHandler;

    public ClientChannelInitializer(ClientPacketHandler clientPacketHandler)
    {
        this.clientPacketHandler = clientPacketHandler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel)
    {
        socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(32*1024*1024,0,4,0,4));
        socketChannel.pipeline().addLast(new LengthFieldPrepender(4));
        socketChannel.pipeline().addLast(new PacketByteBufDecoder());
        socketChannel.pipeline().addLast(new PacketByteBufEncoder());
        socketChannel.pipeline().addLast(this.clientPacketHandler);

    }
}
