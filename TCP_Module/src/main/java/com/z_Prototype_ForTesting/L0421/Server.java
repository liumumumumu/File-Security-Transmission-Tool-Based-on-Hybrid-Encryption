package com.z_Prototype_ForTesting.L0421;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Server
{

    public static void  main(String[] args)
    {
        //1.启动器；负责组装Netty组建，启动服务器
        new ServerBootstrap()
                //2. NioEventLoopGroup就是指 BossEventLoop, WorkerEventLoop(selector, thread);
                .group(new NioEventLoopGroup())
                //3.选择 服务器的ServerSocket的实现
                .channel(NioServerSocketChannel.class)
                //4. Boss负责处理连接，Worker(Child)负责处理读写;决定了worker(child)能执行哪些操作(handler)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {//5.代表了和客户端进行数据读写的通道的Initializer初始化,负责添加其他的handler
                    @Override
                    //连接建立后调用
                    public void initChannel(NioSocketChannel ch) {
                        //6.添加具体的handler
                        ch.pipeline().addLast(new StringDecoder());//将传输过来的ByteBuf转换为字符串
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {//自定义的handler
                            @Override
                            //处理读事件
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                super.channelRead(ctx, msg);
                                //打印上一步转换好的字符串
                                log.info(msg.toString());
                            }
                        });
                    }
                })
                //7.绑定监听端口
                .bind(8080);
    }
}
