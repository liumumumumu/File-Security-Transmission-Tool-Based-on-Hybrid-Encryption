package com.server;

import com.common.config.ServerProperties;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-04-23
 * Purpose: 真正启动server监听连接
 * 启动Bootstrap, 绑定host, port
 *
 **/

@Component
public class NettyRelayServer
{
    private final ServerProperties serverProperties;
    private final ServerChannelInitializer serverChannelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public NettyRelayServer(ServerProperties serverProperties, ServerChannelInitializer serverChannelInitializer) {
        this.serverProperties = serverProperties;
        this.serverChannelInitializer = serverChannelInitializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startIfEnable() throws InterruptedException
    {
        if(! serverProperties.isEnabled())
        {
            return ;
        }

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        serverChannel=bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(serverChannelInitializer)
                .bind(serverProperties.getBindHost(), serverProperties.getBindPort())
                .sync()
                .channel();
    }

    @PreDestroy
    public void stop()
    {
        if(serverChannel!=null)
        {
            serverChannel.close();
        }
        if(bossGroup!=null)
        {
            bossGroup.shutdownGracefully();
        }
        if(workerGroup!=null)
        {
            workerGroup.shutdownGracefully();
        }
    }

}
