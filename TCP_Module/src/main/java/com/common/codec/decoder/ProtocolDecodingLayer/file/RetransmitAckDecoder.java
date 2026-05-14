package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.RetransmitAckPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class RetransmitAckDecoder extends Decoder
{
    @Override
    public RetransmitAckPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Retransmit_Ack) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by RetransmitAckDecoder");
        }

        String transferId = readString(in);
        int acceptedLength = in.readInt();
        if (acceptedLength != 1) {
            throw new IllegalStateException("accepted field length must be 1, but got " + acceptedLength);
        }
        byte acceptedByte = in.readByte();
        if (acceptedByte != 0 && acceptedByte != 1) {
            throw new IllegalStateException("accepted field value must be 0 or 1, but got " + acceptedByte);
        }

        int startBlockIdLength = in.readInt();
        if (startBlockIdLength != 4) {
            throw new IllegalStateException("startBlockId field length must be 4, but got " + startBlockIdLength);
        }
        int startBlockId = in.readInt();
        return new RetransmitAckPacket(transferId, acceptedByte == 1, startBlockId, readString(in));
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
