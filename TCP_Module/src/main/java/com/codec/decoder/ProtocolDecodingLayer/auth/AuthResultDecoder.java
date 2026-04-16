package com.codec.decoder.ProtocolDecodingLayer.auth;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.auth.AuthResultPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class AuthResultDecoder extends Decoder
{
    @Override
    public AuthResultPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Auth_Result)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by AuthResultDecoder");
        }

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
        boolean success = (successByte == 1);

        int messageLength = in.readInt();
        byte[] messageBytes = new byte[messageLength];
        in.readBytes(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        return new AuthResultPacket(message, success);
    }
}
