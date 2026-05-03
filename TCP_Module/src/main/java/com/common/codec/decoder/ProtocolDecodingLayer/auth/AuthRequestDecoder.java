package com.common.codec.decoder.ProtocolDecodingLayer.auth;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.auth.AuthRequestPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class AuthRequestDecoder extends Decoder
{
    @Override
    public AuthRequestPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Auth_Request)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by AuthRequestDecoder");
        }

        int publicKeyBytesLength = in.readInt();
        byte[] publicKeyBytes = new byte[publicKeyBytesLength];
        in.readBytes(publicKeyBytes);
        String publicKey = new String(publicKeyBytes, StandardCharsets.UTF_8);

        int deviceIdBytesLength = in.readInt();
        byte[] deviceIdBytes = new byte[deviceIdBytesLength];
        in.readBytes(deviceIdBytes);
        String deviceId = new String(deviceIdBytes, StandardCharsets.UTF_8);

        return new AuthRequestPacket(deviceId, publicKey);
    }
}
