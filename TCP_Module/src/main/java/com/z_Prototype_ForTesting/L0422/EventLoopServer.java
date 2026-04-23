package com.z_Prototype_ForTesting.L0422;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

@Slf4j
public class EventLoopServer
{
    public static void main(String[] args)
    {
        //细分2: 再创建一个新的EventLoopGroup来处理耗时较长的操作
        EventLoopGroup group= new DefaultEventLoop();//只能处理普通任务和定时任务
        new ServerBootstrap()
                //EventLoop的职责最好细分成boss和 worker
                //细分1:     boss只负责ServerSocketChannel 上accept事件，worker只负责 socketChannel上的读写操作

                .group(new NioEventLoopGroup(), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    public void initChannel(NioSocketChannel ch)
                    {
                        ch.pipeline().addLast("handler1", new ChannelInboundHandlerAdapter(){
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg)throws  Exception
                            {
                                ByteBuf byteBuffer = (ByteBuf) msg;
                                log.info(byteBuffer.toString());
                                ctx.fireChannelRead(msg);//将消息传递给下一个handler
                            }
                        }).addLast(group, "handler2", new ChannelInboundHandlerAdapter(){
                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg)throws  Exception
                            {
                                ByteBuf byteBuffer = (ByteBuf) msg;
                                log.info(byteBuffer.toString());
                            }
                        });

                    }
                })
                .bind(8080);

    }
}
