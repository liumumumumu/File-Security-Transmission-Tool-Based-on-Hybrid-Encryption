package com.protocol.heartbeat;

import com.protocol.MessageType;
import com.protocol.Packet;

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
