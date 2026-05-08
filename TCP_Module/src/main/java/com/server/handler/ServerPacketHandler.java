package com.server.handler;


import com.common.protocol.Packet;
import com.server.service.ServerRoutingService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-05-02
 * Purpose: Netty管道里业务的入口（服务端）
 * 服务端收到客户端发来的Packet后统一交给ServerRoutingService处理
 *
 * */

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
        serverRoutingService.handlePacket(ctx,msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        serverRoutingService.handleDisconnect(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        serverRoutingService.handleDisconnect(ctx.channel());
        ctx.close();
    }

}
