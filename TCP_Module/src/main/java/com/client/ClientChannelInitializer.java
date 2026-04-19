package com.client;

import com.common.codec.netty.PacketByteBufDecoder;
import com.common.codec.netty.PacketByteBufEncoder;
import com.common.protocol.Packet;
import com.handler.ClientPacketHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: Netty连接初始化处理链，在TCP连接建立后规定链条上的数据按照什么样的顺序进行拆包，封包，编码，解码和其他的处理
 * Channel管道配置类
 * 解决TCP沾包/ 半包的问题
 * 给发送数据添加长度字段
 * 把收到的二进制数据解码成协议对象
 * 把要发送的协议对象编码成二进制数据
 *
 **/

public class ClientChannelInitializer extends ChannelInitializer<SocketChannel>//Netty 在新连接创建完成后，会自动调用它的initChannel
{
    private final ClientPacketHandler clientPacketHandler;//客户端自定义的业务处理器

    public ClientChannelInitializer(ClientPacketHandler clientPacketHandler)
    {
        this.clientPacketHandler = clientPacketHandler;
    }

    @Override
    protected void initChannel(SocketChannel socketChannel)
    {
        //基于长度字段的拆包器，解决沾包和半包问题
        /*
        32 * 1024 * 1024: 单个数据帧最大长度是32MB(暂定)，超过就会报报文异常
        0: 长度字段从数据包第0个字节开始
        4: 长度字段占4个字节; 在整个报文最前面，有一个专门用于拆包的长度字段，这个长度字段本身占 4 字节。
        0: 长度挑战值
        4: 解码后跳过前4个字节
        * */
        socketChannel.pipeline().addLast(new LengthFieldBasedFrameDecoder(32*1024*1024,0,4,0,4));
        //在发送数据时，自动在消息前面加上4字节的长度字段
        socketChannel.pipeline().addLast(new LengthFieldPrepender(4));//变成[4字节长度][协议内容]
        //使用统一的协议解码器，再进入各自的协议解码器
        socketChannel.pipeline().addLast(new PacketByteBufDecoder());
        //使用统一的协议编码器，再进入各自的协议编码器
        socketChannel.pipeline().addLast(new PacketByteBufEncoder());
        //客户端自定义业务处理器
        socketChannel.pipeline().addLast(this.clientPacketHandler);
    }
}
