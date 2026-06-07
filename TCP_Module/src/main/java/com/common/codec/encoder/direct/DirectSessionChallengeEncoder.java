package com.common.codec.encoder.direct;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionChallengePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DirectSessionChallengeEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        DirectSessionChallengePacket value = (DirectSessionChallengePacket) packet;
        ByteBuf out = Unpooled.buffer();
        out.writeByte(value.getMessageType());
        DirectSessionEncoderSupport.writeString(out, value.getInviteId());
        DirectSessionEncoderSupport.writeString(out, value.getSessionId());
        DirectSessionEncoderSupport.writeString(out, value.getChallenge());
        return out;
    }
}
