package com.client.transport;

import com.client.ClientConnectionManager;
import com.common.protocol.Packet;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 中继传输通道适配器,将原来的ClientConnectionManager包装成统一的PacketTransport接口
 *
 * */

public class ServerRelayTransport implements PacketTransport
{
    private final ClientConnectionManager clientConnectionManager;//负责客户端和中心服务器之间的连接，认证和发包

    public ServerRelayTransport(ClientConnectionManager clientConnectionManager)
    {
        this.clientConnectionManager = clientConnectionManager;
    }

    @Override
    public void send(Packet packet)
    {
        clientConnectionManager.send(packet);
    }

    @Override
    public boolean isActive()
    {
        return clientConnectionManager.isAuthenticated();
    }

    @Override
    public String remoteDeviceId()
    {
        return "server";
    }

    @Override
    public TransportMode mode()
    {
        return TransportMode.SERVER_RELAY;
    }
}
