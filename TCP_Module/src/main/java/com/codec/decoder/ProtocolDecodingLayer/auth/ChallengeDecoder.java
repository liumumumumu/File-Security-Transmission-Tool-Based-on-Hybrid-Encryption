package com.codec.decoder.ProtocolDecodingLayer.auth;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.auth.ChallengePacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class ChallengeDecoder extends Decoder
{
    @Override
    public ChallengePacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Challenge)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by ChallengeDecoder");
        }

        int challengeIdLength = in.readInt();
        byte[] challengeIdBytes = new byte[challengeIdLength];
        in.readBytes(challengeIdBytes);
        String challengeId = new String(challengeIdBytes, StandardCharsets.UTF_8);

        int challengeLength = in.readInt();
        byte[] challengeBytes = new byte[challengeLength];
        in.readBytes(challengeBytes);
        String challenge = new String(challengeBytes, StandardCharsets.UTF_8);

        return new ChallengePacket(challengeId, challenge);
    }
}
