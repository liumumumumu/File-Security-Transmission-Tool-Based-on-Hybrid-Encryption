package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.TransferCancelAckPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class TransferCancelAckEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof TransferCancelAckPacket transferCancelAckPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type TransferCancelAckPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(transferCancelAckPacket.getMessageType());
        writeString(frame, transferCancelAckPacket.getTransferId());
        writeString(frame, transferCancelAckPacket.getAckByDeviceId());
        writeString(frame, transferCancelAckPacket.getStatus());
        writeString(frame, transferCancelAckPacket.getMessage());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}
