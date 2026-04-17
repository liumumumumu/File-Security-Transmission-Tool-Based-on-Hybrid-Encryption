package com.codec.decoder.ProtocolDecodingLayer.file;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.file.FileBlockPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class FileBlockDecoder extends Decoder
{
    @Override
    public FileBlockPacket decode(ByteBuf in)
    {
        byte messageType=in.readByte();
        if(messageType != MessageType.File_Block)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by FileBlockDecoder");
        }

        int transferIdLength = in.readInt();
        byte[] transferIdBytes = new byte[transferIdLength];
        in.readBytes(transferIdBytes);
        String transferId = new String(transferIdBytes, StandardCharsets.UTF_8);

        int blockIdLength = in.readInt();
        if (blockIdLength != 4)
        {
            throw new IllegalStateException(
                    "success field length must be 4, but got " + blockIdLength
            );
        }
        int blockId=in.readInt();

        int nonceLength = in.readInt();
        byte[] nonce = new byte[nonceLength];
        in.readBytes(nonce);

        int ciphertextLength = in.readInt();
        byte[] cipherText = new byte[ciphertextLength];
        in.readBytes(cipherText);

        int tagLength = in.readInt();
        byte[] tag = new byte[tagLength];
        in.readBytes(tag);

        return new FileBlockPacket(blockId, cipherText, nonce, tag, transferId);
    }
}
