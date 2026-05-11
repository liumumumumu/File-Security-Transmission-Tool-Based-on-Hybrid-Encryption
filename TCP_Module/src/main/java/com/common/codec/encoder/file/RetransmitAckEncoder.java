package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.RetransmitAckPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class RetransmitAckEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof RetransmitAckPacket retransmitAckPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type RetransmitAckPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(retransmitAckPacket.getMessageType());
        writeString(frame, retransmitAckPacket.getTransferId());
        frame.writeInt(1);
        frame.writeByte(retransmitAckPacket.isAccepted() ? 1 : 0);
        frame.writeInt(4);
        frame.writeInt(retransmitAckPacket.getStartBlockId());
        writeString(frame, retransmitAckPacket.getMessage());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}
