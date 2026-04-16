package com.codec.encoder.heartbeat;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.heartbeat.PongPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PongEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof PongPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type PongPacket");
        }

        PongPacket pongPacket = (PongPacket)packet;
        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(pongPacket.getMessageType());
        return startFrame;
    }
}
