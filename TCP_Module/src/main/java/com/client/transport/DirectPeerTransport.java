package com.client.transport;


import com.common.protocol.Packet;
import io.netty.channel.Channel;

/**
 * Author: LQH
 * Date: 2026-05-21
 * Purpose: IPv6直连模式下的发包通道实现类
 *
 * */

public class DirectPeerTransport implements PacketTransport
{
    private final Channel channel;//Netty的TCP连接通道
    private final String remoteDeviceId;//对端的设备ID

    public DirectPeerTransport(Channel channel, String remoteDeviceId)
    {
        this.channel = channel;
        this.remoteDeviceId = remoteDeviceId;
    }

    @Override
    public void send(Packet packet)//发送协议包
    {
        if(!isActive())
        {
            throw new IllegalStateException("Direct peer channel is not active");
        }
        channel.writeAndFlush(packet);
    }

    @Override
    public boolean isActive()//判断通道是否可用
    {
        return channel != null && channel.isActive();
    }

    @Override
    public String remoteDeviceId()//返回对端的设备ID
    {
        return remoteDeviceId;
    }

    @Override
    public TransportMode mode()//返回当前的传输模式
    {
        return TransportMode.DIRECT_PEER;
    }

    public Channel channel()//返回Netty的Channel，用于直接关闭连接
    {
        return channel;
    }
}

