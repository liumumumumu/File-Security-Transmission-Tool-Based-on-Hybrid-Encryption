package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.AckPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class AckDecoder extends Decoder
{
    @Override
    public AckPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.ACk)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by AckDecoder");
        }

        int transferIdlength = in.readInt();
        byte[] transferIdBytes = new byte[transferIdlength];
        in.readBytes(transferIdBytes);
        String transferId = new String(transferIdBytes, StandardCharsets.UTF_8);

        int blockedIdLength = in.readInt();
        if (blockedIdLength != 4)
        {
            throw new IllegalStateException(
                    "success field length must be 4, but got " + blockedIdLength
            );
        }
        int blockedId=in.readInt();

        int successLength = in.readInt();
        if (successLength != 1)
        {
            throw new IllegalStateException(
                    "success field length must be 1, but got " + successLength
            );
        }
        byte successByte = in.readByte();
        if (successByte != 0 && successByte != 1)
        {
            throw new IllegalStateException(
                    "success field value must be 0 or 1, but got " + successByte
            );
        }
        boolean success= (successByte==1);

        return new AckPacket(blockedId, success, transferId);
    }
}
