package com.codec.encoder.auth;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.auth.AuthResponsePacket;
import com.protocol.auth.AuthResultPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

public class AuthResultEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof AuthResultPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type AuthResultPacket");
        }
        AuthResultPacket AResultPacket = (AuthResultPacket) packet;

        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(AResultPacket.getMessageType());

        startFrame.writeInt(1);
        startFrame.writeByte(AResultPacket.isSuccess() ? 1 : 0);

        byte[] messageBytes=AResultPacket.getMessage().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(messageBytes.length);
        startFrame.writeBytes(messageBytes);

        return startFrame;
    }
}
