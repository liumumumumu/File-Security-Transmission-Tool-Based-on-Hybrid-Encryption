package com.z_Prototype_ForTesting.L0421;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringEncoder;

public class Client
{
    public static void main(String[] args)
    {
        try
        {

            //1.启动类
            new Bootstrap()
                    //2.添加EventLoop
                    .group(new NioEventLoopGroup())
                    //3.选择客户端channel实现
                    .channel(NioSocketChannel.class)
                    //4.添加处理器
                    .handler(new ChannelInitializer<NioSocketChannel>() {
                        @Override //在连接建立后被调用
                        public void initChannel(NioSocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new StringEncoder());
                        }
                    })
                    //5.连接服务器
                    .connect("localhost", 8080)
                    .sync()//同步方法，直到连接建立
                    .channel()//代表连接对象
                    //向服务器发送数据
                    .writeAndFlush("Sender: hello Netty");
                    //收发数据都走到handler里面

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

    }
}
