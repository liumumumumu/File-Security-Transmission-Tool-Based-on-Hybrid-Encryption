package com.common.codec.decoder.ProtocolDecodingLayer.file;


import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.IncomingTransferRequestPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class IncomingTransferRequestDecoder extends Decoder
{
    @Override
    public IncomingTransferRequestPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Incoming_Transfer_Request) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by IncomingTransferRequestDecoder");
        }
        return new IncomingTransferRequestPacket(
                readString(in),
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