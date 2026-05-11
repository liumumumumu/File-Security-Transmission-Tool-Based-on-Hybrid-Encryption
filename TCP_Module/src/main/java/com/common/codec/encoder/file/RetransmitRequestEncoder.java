package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.RetransmitRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class RetransmitRequestEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof RetransmitRequestPacket retransmitRequestPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type RetransmitRequestPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(retransmitRequestPacket.getMessageType());
        writeString(frame, retransmitRequestPacket.getTransferId());
        frame.writeInt(4);
        frame.writeInt(retransmitRequestPacket.getStartBlockId());
        writeString(frame, retransmitRequestPacket.getReason());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}
