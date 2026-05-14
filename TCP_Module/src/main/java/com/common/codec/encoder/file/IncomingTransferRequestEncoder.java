package com.common.codec.encoder.file;


import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.IncomingTransferRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class IncomingTransferRequestEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof IncomingTransferRequestPacket requestPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type IncomingTransferRequestPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(requestPacket.getMessageType());
        writeString(frame, requestPacket.getTransferId());
        writeString(frame, requestPacket.getSenderDeviceId());
        writeString(frame, requestPacket.getTargetAccountId());
        writeString(frame, requestPacket.getFileName());
        frame.writeLong(requestPacket.getFileSize());
        frame.writeInt(requestPacket.getTotalBlocks());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}