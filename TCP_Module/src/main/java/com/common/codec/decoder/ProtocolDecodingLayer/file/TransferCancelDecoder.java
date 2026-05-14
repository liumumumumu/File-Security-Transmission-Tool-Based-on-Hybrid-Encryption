package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.TransferCancelPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class TransferCancelDecoder extends Decoder
{
    @Override
    public TransferCancelPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Transfer_Cancel) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by TransferCancelDecoder");
        }

        return new TransferCancelPacket(readString(in), readString(in));
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
