package com.z_Prototype_ForTesting;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.nio.charset.StandardCharsets;

/**
 * 最小化的 Netty 文件接收服务端。
 *
 * 当前设计目标：
 * 1. 只演示最基础的 TCP 文件传输流程。
 * 2. 一个客户端连接进来后，按自定义协议上传一个文件。
 * 3. 服务端接收后写入本地 uploads 目录。
 *
 * 这里不负责真正处理“文件内容协议”，协议解析由 FileServerHandler 完成。
 * 本类只负责：
 * 1. 启动服务端监听端口
 * 2. 配置 Netty 的线程模型
 * 3. 配置 ChannelPipeline 中的解码器和业务处理器
 */
public class FileServer {

    public static void main(String[] args) throws Exception {
        // 服务端监听端口。
        int port = 9000;

        // bossGroup 只负责接收客户端连接请求。
        // 参数 1 表示只开 1 个线程来 accept 连接，对于这个原型足够了。
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);

        // workerGroup 负责真正的网络读写和业务处理。
        // 不传参数时，Netty 会根据 CPU 核数自动分配线程数。
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            //创建一个Netty服务端起动器，用来配置并启动TCP服务器
            ServerBootstrap bootstrap = new ServerBootstrap();

            // 绑定线程组：
            // 第一个是 bossGroup，第二个是 workerGroup。
            bootstrap.group(bossGroup, workerGroup)
                    // 指定服务端使用 NIO 模式的 ServerSocketChannel。
                    .channel(NioServerSocketChannel.class)//ServerChannel来监听TCP连接，这里的意思是使用Java NIO的方式处理网络，底层基于Seleector,适合非阻塞IO
                    // 服务端半连接队列长度。
                    // 在这个最小示例里不是重点，但作为标准配置保留。
                    .option(ChannelOption.SO_BACKLOG, 128)//监听socket的连接等待队列大小，设为128，（同一时刻，在“等待被服务端正式接收”的连接请求，最多允许积压多少个。）
                    //作用：给“每一个新接入的客户端连接”初始化处理链，这个就是初始化当前连接的ChannelPipeline; 服务器端有两类Channel 1. 监听连接的服务端Channel; 2. 每个客户端连接进来后生成的子Channel
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        //配置解码器，编码器，.....
                        protected void initChannel(SocketChannel ch) {
                            // 每当有一个新的客户端连接建立成功，都会进入这里初始化该连接的处理链。
                            //
                            // 本项目的自定义协议格式是：
                            // [4字节长度][真正消息体]
                            //
                            // 也就是说，发送方在每一帧消息前面都加一个 4 字节长度字段。
                            // 服务端需要先根据这 4 字节把一帧完整消息切出来，避免 TCP 粘包/拆包问题。
                            ch.pipeline().addLast(
                                    new LengthFieldBasedFrameDecoder(
                                            // 单帧允许的最大长度：10MB。
                                            // 如果某一帧超过这个大小，解码器会抛异常。
                                            10 * 1024 * 1024,
                                            // 长度字段相对帧起始位置的偏移量。
                                            // 我们的长度字段就在最开头，所以偏移量是 0。
                                            0,
                                            // 长度字段本身占 4 字节。
                                            4,
                                            // 长度字段后面没有额外要跳过或补偿的内容，所以是 0。
                                            0,
                                            // 解码完成后，把前面的长度字段剥掉。
                                            // 这样后面的 handler 收到的就是“纯消息体”。
                                            4
                                    )
                            );

                            // 服务端给客户端回响应时，直接写字符串。
                            // StringEncoder 会把 Java String 按 UTF-8 编码成 ByteBuf。
                            ch.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));

                            // 真正负责接收文件协议并写入磁盘的业务处理器。
                            ch.pipeline().addLast(new FileServerHandler());
                        }
                    });

            // 绑定端口并同步等待绑定完成。
            // bind(...).sync() 确保端口真正监听成功后再继续往下执行。
            //
            // closeFuture().sync() 会阻塞当前线程，
            // 直到服务端 Channel 被关闭，相当于“让服务一直跑着”。
            bootstrap.bind(port).sync().channel().closeFuture().sync();//启动服务端监听端口，然后让主线程一直等到服务端关闭
        } finally {
            // 优雅关闭线程池，释放 Netty 占用的线程和 Selector 等资源。
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
