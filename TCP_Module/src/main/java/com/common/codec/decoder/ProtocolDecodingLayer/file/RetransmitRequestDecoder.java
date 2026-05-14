package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.RetransmitRequestPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class RetransmitRequestDecoder extends Decoder
{
    @Override
    public RetransmitRequestPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Retransmit_Request) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by RetransmitRequestDecoder");
        }

        String transferId = readString(in);
        int startBlockIdLength = in.readInt();
        if (startBlockIdLength != 4) {
            throw new IllegalStateException("startBlockId field length must be 4, but got " + startBlockIdLength);
        }
        int startBlockId = in.readInt();
        return new RetransmitRequestPacket(transferId, startBlockId, readString(in));
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
