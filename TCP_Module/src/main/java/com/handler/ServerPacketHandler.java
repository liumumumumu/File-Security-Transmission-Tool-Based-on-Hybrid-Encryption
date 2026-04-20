package com.handler;


import com.common.protocol.Packet;
import com.service.ServerRoutingService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class ServerPacketHandler extends SimpleChannelInboundHandler<Packet>
{
    private final ServerRoutingService serverRoutingService;

    public ServerPacketHandler(ServerRoutingService serverRoutingService)
    {
        this.serverRoutingService = serverRoutingService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg)
    {

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {

    }

}
