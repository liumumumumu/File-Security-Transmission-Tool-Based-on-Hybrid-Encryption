package com.common.codec.encoder.auth;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.auth.AuthResponsePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class AuthResponseEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof AuthResponsePacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type AuthResponsePacket");
        }
        AuthResponsePacket AResponsePacket = (AuthResponsePacket) packet;

        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(AResponsePacket.getMessageType());

        byte []publicKeyBytes=AResponsePacket.getPublicKey().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(publicKeyBytes.length);
        startFrame.writeBytes(publicKeyBytes);

        byte[] challengeIdBytes=AResponsePacket.getChallengeId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(challengeIdBytes.length);
        startFrame.writeBytes(challengeIdBytes);

        byte[] signatureBytes=AResponsePacket.getSignature().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(signatureBytes.length);
        startFrame.writeBytes(signatureBytes);
        return startFrame;
    }
}
