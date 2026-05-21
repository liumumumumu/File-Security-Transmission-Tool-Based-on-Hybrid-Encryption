package com.client.transport;

import com.common.protocol.Packet;

/**
 * Author: LQH
 * Date: 2026-05-21
 * Purpose: 发包接口，将发送协议包抽象出来，让文件传输业务层不用管文件传输到底走那个模式
 *
 * */

public interface PacketTransport
{
    void send(Packet packet);//发送包
    boolean isActive();//判断通道是否可用
    String remoteDeviceId();//返回对端的设备ID
    TransportMode mode();//返回当前的传输模式
}
