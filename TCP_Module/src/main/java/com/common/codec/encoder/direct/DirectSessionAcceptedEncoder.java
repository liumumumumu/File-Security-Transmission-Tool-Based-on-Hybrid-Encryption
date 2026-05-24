package com.common.codec.encoder.direct;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionAcceptedPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DirectSessionAcceptedEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        DirectSessionAcceptedPacket value = (DirectSessionAcceptedPacket) packet;
        ByteBuf out = Unpooled.buffer();
        out.writeByte(value.getMessageType());
        DirectSessionEncoderSupport.writeString(out, value.getInviteId());
        DirectSessionEncoderSupport.writeString(out, value.getSessionId());
        out.writeBoolean(value.isAccepted());
        DirectSessionEncoderSupport.writeString(out, value.getMessage());
        DirectSessionEncoderSupport.writeString(out, value.getReceiverAccountId());
        DirectSessionEncoderSupport.writeString(out, value.getReceiverDeviceId());
        DirectSessionEncoderSupport.writeString(out, value.getReceiverPublicKey());
        return out;
    }
}
