package com.protocol.heartbeat;

import com.protocol.MessageType;
import com.protocol.Packet;

public class PongPacket extends Packet
{
    @Override
    public byte getMessageType()
    {
        return MessageType.Pong;
    }

    @Override
    public String toString() {
        return "PongPacket{}";
    }
}
