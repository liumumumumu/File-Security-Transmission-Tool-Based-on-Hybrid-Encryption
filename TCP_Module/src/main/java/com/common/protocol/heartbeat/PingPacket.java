package com.common.protocol.heartbeat;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class PingPacket extends Packet
{
    @Override
    public byte getMessageType()
    {
        return MessageType.Ping;
    }

    @Override
    public String toString() {
        return "PingPacket{}";
    }
}
