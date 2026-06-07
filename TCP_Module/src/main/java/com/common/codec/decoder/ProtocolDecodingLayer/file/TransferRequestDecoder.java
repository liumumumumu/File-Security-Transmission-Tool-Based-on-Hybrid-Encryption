package com.common.codec.decoder.ProtocolDecodingLayer.file;


import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.TransferRequestPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class TransferRequestDecoder extends Decoder
{
    @Override
    public TransferRequestPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Transfer_Request) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by TransferRequestDecoder");
        }
        return new TransferRequestPacket(
                readString(in),
                readString(in),
                readString(in),
                in.readLong(),
                in.readInt()
        );
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}