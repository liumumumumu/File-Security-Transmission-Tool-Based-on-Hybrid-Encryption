package com.codec.encoder.auth;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.auth.AuthResultPacket;
import com.protocol.auth.ChallengePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class ChallengeEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof ChallengePacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type ChallengePacket");
        }
        ChallengePacket challengePacket = (ChallengePacket) packet;

        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(challengePacket.getMessageType());

        byte[] challengeIdBytes = challengePacket.getChallengeId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(challengeIdBytes.length);
        startFrame.writeBytes(challengeIdBytes);

        byte[] challengeBytes = challengePacket.getChallenge().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(challengeBytes.length);
        startFrame.writeBytes(challengeBytes);
        return startFrame;
    }
}
