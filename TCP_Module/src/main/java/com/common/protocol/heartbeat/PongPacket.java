package com.common.protocol.heartbeat;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

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
