package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.AckPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class AckEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof AckPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type AckPacket");
        }

        AckPacket ackPacket = (AckPacket) packet;
        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(ackPacket.getMessageType());

        byte[] transferIdBytes = ackPacket.getTransferId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(transferIdBytes.length);
        startFrame.writeBytes(transferIdBytes);

        startFrame.writeInt(4);
        startFrame.writeInt(ackPacket.getBlockedId());

        startFrame.writeInt(1);
        startFrame.writeByte(ackPacket.isSuccess()? 1: 0);

        return startFrame;
    }
}
