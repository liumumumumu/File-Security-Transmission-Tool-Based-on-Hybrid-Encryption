package com.client.direct;

import com.common.codec.netty.PacketByteBufDecoder;
import com.common.codec.netty.PacketByteBufEncoder;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-05-20
 * Purpose: Netty通道初始化工具，当一个新的TCP连接建立是，为这个连接的ChannelPipeline配置一组处理器，
 * 用来拆包，封包，字节流编码
 *
 * */

@Component
public class DirectPeerChannelInitializer extends ChannelInitializer<SocketChannel> //ChannelInitializer是Netty的一个特殊处理器，用于初始化新的Channel
{
    private final DirectPeerPacketHandler directPeerPacketHandler;//业务处理器，用来处理已经解码出来的数据包；接收对端发来的文件传输请求，处理加密握手消息，处理文件块数据，处理传输完成、错误、断开等状态

    public DirectPeerChannelInitializer(DirectPeerPacketHandler directPeerPacketHandler)
    {
        this.directPeerPacketHandler = directPeerPacketHandler;
    }

    //连接建立后，由Netty调用初始化函数
    @Override
    protected void initChannel(SocketChannel socketChannel)
    {
        socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(32 * 1024 * 1024, 0, 4, 0, 4));//解决TCP沾包的问题；最大帧长度32MB,字段长度的偏移量为0,长度字段占4个字节,长度修正值为0,解码后丢弃前4个字节的长度字段
        socketChannel.pipeline().addLast(new LengthFieldPrepender(4));//发送数据的时候自动添加长度字段(4个字节)
        socketChannel.pipeline().addLast(new PacketByteBufDecoder());//将字节数据解码成业务数据包
        socketChannel.pipeline().addLast(new PacketByteBufEncoder());//将业务数据包编码成字节数据
        socketChannel.pipeline().addLast(directPeerPacketHandler);
    }
}
