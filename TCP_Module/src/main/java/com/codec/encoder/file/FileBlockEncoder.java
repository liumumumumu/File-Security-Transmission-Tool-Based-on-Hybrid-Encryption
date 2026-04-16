package com.codec.encoder.file;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.file.FileAcceptPacket;
import com.protocol.file.FileBlockPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class FileBlockEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof FileBlockPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type FileBlockPacket");
        }

        FileBlockPacket FBPacket = (FileBlockPacket) packet;
        ByteBuf startFrame = Unpooled.buffer();

        startFrame.writeByte(FBPacket.getMessageType());

        byte[] transferIdBytes=FBPacket.getTransferId().getBytes(StandardCharsets.US_ASCII);
        startFrame.writeInt(transferIdBytes.length);
        startFrame.writeBytes(transferIdBytes);

        startFrame.writeInt(4);
        startFrame.writeInt(FBPacket.getBlockId());

        startFrame.writeInt(FBPacket.getNonce().length);
        startFrame.writeBytes(FBPacket.getNonce());

        startFrame.writeInt(FBPacket.getCiphertext().length);
        startFrame.writeBytes(FBPacket.getCiphertext());

        startFrame.writeInt(FBPacket.getTag().length);
        startFrame.writeBytes(FBPacket.getTag());

        return startFrame;
    }
}
