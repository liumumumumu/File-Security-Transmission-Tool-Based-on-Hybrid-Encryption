package com.codec.encoder.file;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.file.FilleOfferPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class FileOfferEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof FilleOfferPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type FilleOfferPacket");
        }

        FilleOfferPacket FOPacket = (FilleOfferPacket)packet;

        ByteBuf startFrame= Unpooled.buffer();
        startFrame.writeByte(FOPacket.getMessageType());

        byte[] transferIdBytes=FOPacket.getTransferId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(transferIdBytes.length);
        startFrame.writeBytes(transferIdBytes);

        byte[] senderPublicKeyBytes=FOPacket.getSenderPublicKey().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(senderPublicKeyBytes.length);
        startFrame.writeBytes(senderPublicKeyBytes);

        byte[] receiverPublicKeyBytes=FOPacket.getReceiverPublicKey().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(receiverPublicKeyBytes.length);
        startFrame.writeBytes(receiverPublicKeyBytes);

        byte[] encryptedSessionKeyBytes=FOPacket.getEncryptedSessionKey().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(encryptedSessionKeyBytes.length);
        startFrame.writeBytes(encryptedSessionKeyBytes);

        byte[] fileNameBytes=FOPacket.getFileName().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(fileNameBytes.length);
        startFrame.writeBytes(fileNameBytes);

        startFrame.writeInt(8);
        startFrame.writeLong(FOPacket.getFileSize());

        startFrame.writeInt(4);
        startFrame.writeInt(FOPacket.getTotalBlocks());

        return startFrame;
    }

}
