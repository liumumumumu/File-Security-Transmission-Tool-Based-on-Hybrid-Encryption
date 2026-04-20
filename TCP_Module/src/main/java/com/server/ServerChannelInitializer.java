package com.server;

import com.common.codec.netty.PacketByteBufDecoder;
import com.common.codec.netty.PacketByteBufEncoder;
import com.handler.ServerPacketHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: 在服务端有新的客户端连接建立时，初始化这条连接的Netty处理管道
 * 通信链路配置类
 **/


@Component
public class ServerChannelInitializer extends ChannelInitializer<SocketChannel>
{
    private final ServerPacketHandler serverPacketHandler;

    public ServerChannelInitializer(ServerPacketHandler serverPacketHandler)
    {
        this.serverPacketHandler = serverPacketHandler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel)
    {
        socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(32 * 1024 * 1024, 0, 4, 0, 4));
        socketChannel.pipeline().addLast(new LengthFieldPrepender(4));
        socketChannel.pipeline().addLast(new PacketByteBufDecoder());
        socketChannel.pipeline().addLast(new PacketByteBufEncoder());
        socketChannel.pipeline().addLast(this.serverPacketHandler);
    }
}
