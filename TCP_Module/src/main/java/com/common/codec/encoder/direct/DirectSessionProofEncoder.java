package com.common.codec.encoder.direct;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionProofPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DirectSessionProofEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        DirectSessionProofPacket value = (DirectSessionProofPacket) packet;
        ByteBuf out = Unpooled.buffer();
        out.writeByte(value.getMessageType());
        DirectSessionEncoderSupport.writeString(out, value.getInviteId());
        DirectSessionEncoderSupport.writeString(out, value.getSessionId());
        DirectSessionEncoderSupport.writeString(out, value.getSignature());
        return out;
    }
}
