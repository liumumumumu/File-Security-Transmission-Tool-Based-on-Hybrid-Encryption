package com.handler;

import com.client.ClientConnectionManager;
import com.common.protocol.Packet;
import com.common.protocol.auth.ChallengePacket;
import com.service.ClientTransferService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class ClientPacketHandler extends SimpleChannelInboundHandler<Packet>
{
    private final ClientConnectionManager clientConnectionManager;
    private final ClientTransferService clientTransferService;

    public ClientPacketHandler(@Lazy ClientConnectionManager clientConnectionManager, @Lazy ClientTransferService clientTransferService)//延迟加载注解，不要在容器启动时立刻创建这个依赖对象，而是在真正用到它的时候再创建。
    {
        this.clientConnectionManager = clientConnectionManager;
        this.clientTransferService = clientTransferService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg)throws Exception
    {

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx)throws Exception
    {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)throws Exception
    {

    }
}
