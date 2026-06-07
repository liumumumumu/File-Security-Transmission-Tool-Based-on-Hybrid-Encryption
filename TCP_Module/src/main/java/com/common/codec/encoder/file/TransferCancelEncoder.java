package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.TransferCancelPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class TransferCancelEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof TransferCancelPacket transferCancelPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type TransferCancelPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(transferCancelPacket.getMessageType());
        writeString(frame, transferCancelPacket.getTransferId());
        writeString(frame, transferCancelPacket.getReason());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}
