package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.FileOfferPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class FileOfferDecoder extends Decoder
{
    @Override
    public FileOfferPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.File_Offer)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by FileOfferDecoder");
        }

        int transferIdLength = in.readInt();
        byte[] transferIdBytes = new byte[transferIdLength];
        in.readBytes(transferIdBytes);
        String transferId = new String(transferIdBytes, StandardCharsets.UTF_8);

        int senderPublicKeyLength = in.readInt();
        byte[] senderPublicKeyBytes = new byte[senderPublicKeyLength];
        in.readBytes(senderPublicKeyBytes);
        String senderPublicKey = new String(senderPublicKeyBytes, StandardCharsets.UTF_8);

        int receiverPublicKeyLength = in.readInt();
        byte[] receiverPublicKeyBytes = new byte[receiverPublicKeyLength];
        in.readBytes(receiverPublicKeyBytes);
        String receiverPublicKey = new String (receiverPublicKeyBytes, StandardCharsets.UTF_8);

        int encryptedSessionKeyLength = in.readInt();
        byte[] encryptedSessionKeyBytes = new byte[encryptedSessionKeyLength];
        in.readBytes(encryptedSessionKeyBytes);
        String encryptedSessionKey = new String (encryptedSessionKeyBytes, StandardCharsets.UTF_8);

        int fileNameLength = in.readInt();
        byte[] fileNameBytes = new byte[fileNameLength];
        in.readBytes(fileNameBytes);
        String fileName = new String (fileNameBytes, StandardCharsets.UTF_8);

        int fileSizeLength = in.readInt();
        if(fileSizeLength != 8)
        {
            throw new IllegalStateException(
                    "success field length must be 8, but got " + fileSizeLength
            );
        }
        long fileSize=in.readLong();

        int totalBlocksLength = in.readInt();
        if(totalBlocksLength != 4)
        {
            throw new IllegalStateException(
                    "success field length must be 4, but got " + fileSizeLength
            );
        }
        int totalBlocks=in.readInt();

        return new FileOfferPacket(encryptedSessionKey, fileName,  fileSize, receiverPublicKey, senderPublicKey, totalBlocks, transferId);
    }
}
