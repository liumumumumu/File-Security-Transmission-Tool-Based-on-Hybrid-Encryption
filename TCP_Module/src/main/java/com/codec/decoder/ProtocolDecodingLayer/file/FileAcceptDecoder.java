package com.codec.decoder.ProtocolDecodingLayer.file;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.file.FileAcceptPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class FileAcceptDecoder extends Decoder
{
    @Override
    public FileAcceptPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.File_Accept)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by FileAcceptDecoder");
        }

        int transferIdLength = in.readInt();
        byte[] transferIdBytes = new byte[transferIdLength];
        in.readBytes(transferIdBytes);
        String transferId=new String(transferIdBytes, StandardCharsets.UTF_8);

        int acceptLength = in.readInt();
        if (acceptLength != 1)
        {
            throw new IllegalStateException(
                    "success field length must be 1, but got " + acceptLength
            );
        }

        byte acceptByte = in.readByte();
        if (acceptByte != 0 && acceptByte != 1)
        {
            throw new IllegalStateException(
                    "success field value must be 0 or 1, but got " + acceptByte
            );
        }
        boolean accept = (acceptByte==1);

        int messageLength = in.readInt();
        byte[] messageBytes = new byte[messageLength];
        in.readBytes(messageBytes);
        String message=new String(messageBytes, StandardCharsets.UTF_8);

        return new FileAcceptPacket(accept, message, transferId);
    }
}
