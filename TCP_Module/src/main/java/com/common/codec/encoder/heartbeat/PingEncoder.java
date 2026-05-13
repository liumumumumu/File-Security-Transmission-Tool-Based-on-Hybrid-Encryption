package com.common.codec.encoder.heartbeat;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.heartbeat.PingPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PingEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof PingPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type PingPacket");
        }

        PingPacket pingPacket = (PingPacket)packet;
        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(pingPacket.getMessageType());
        return startFrame;
    }
}
