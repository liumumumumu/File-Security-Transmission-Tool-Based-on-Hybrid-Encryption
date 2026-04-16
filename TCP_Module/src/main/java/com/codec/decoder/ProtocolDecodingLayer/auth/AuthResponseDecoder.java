package com.codec.decoder.ProtocolDecodingLayer.auth;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.auth.AuthResponsePacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class AuthResponseDecoder extends Decoder
{
    @Override
    public AuthResponsePacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Auth_Response)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by AuthResponseDecoder");
        }

        int publicKeyLength = in.readInt();
        byte[] publicKeyBytes = new byte[publicKeyLength];
        in.readBytes(publicKeyBytes);
        String publicKey = new String(publicKeyBytes, StandardCharsets.UTF_8);

        int challengeIdLength = in.readInt();
        byte[] challengeIdBytes = new byte[challengeIdLength];
        in.readBytes(challengeIdBytes);
        String challengeId = new String(challengeIdBytes, StandardCharsets.UTF_8);

        int signatureLength = in.readInt();
        byte[] signatureBytes = new byte[signatureLength];
        in.readBytes(signatureBytes);
        String signature = new String(signatureBytes, StandardCharsets.UTF_8);

        return new AuthResponsePacket(challengeId, publicKey, signature);
    }
}
