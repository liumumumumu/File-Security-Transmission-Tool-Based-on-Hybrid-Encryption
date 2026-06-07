package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.TransferCancelAckPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class TransferCancelAckDecoder extends Decoder
{
    @Override
    public TransferCancelAckPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Transfer_Cancel_Ack) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by TransferCancelAckDecoder");
        }

        return new TransferCancelAckPacket(readString(in), readString(in), readString(in), readString(in));
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
