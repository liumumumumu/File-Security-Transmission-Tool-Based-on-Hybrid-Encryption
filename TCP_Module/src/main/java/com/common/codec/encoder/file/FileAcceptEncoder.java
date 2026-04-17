package com.common.codec.encoder.file;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.FileAcceptPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class FileAcceptEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof FileAcceptPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type FileAcceptPacket");
        }

        FileAcceptPacket FAPacket = (FileAcceptPacket) packet;

        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(FAPacket.getMessageType());

        byte[] transferIdBytes=FAPacket.getTransferId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(transferIdBytes.length);
        startFrame.writeBytes(transferIdBytes);

        startFrame.writeInt(1);
        startFrame.writeByte(FAPacket.isAccept()? 1: 0);

        byte[] messageBytes=FAPacket.getMessage().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(messageBytes.length);
        startFrame.writeBytes(messageBytes);

        return startFrame;
    }
}
