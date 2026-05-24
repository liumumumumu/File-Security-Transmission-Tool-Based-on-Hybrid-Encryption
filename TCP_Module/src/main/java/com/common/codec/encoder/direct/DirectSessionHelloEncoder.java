package com.common.codec.encoder.direct;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.direct.DirectSessionHelloPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DirectSessionHelloEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        DirectSessionHelloPacket value = (DirectSessionHelloPacket) packet;
        ByteBuf out = Unpooled.buffer();
        out.writeByte(value.getMessageType());
        DirectSessionEncoderSupport.writeString(out, value.getInviteId());
        DirectSessionEncoderSupport.writeString(out, value.getSessionId());
        DirectSessionEncoderSupport.writeString(out, value.getSenderAccountId());
        DirectSessionEncoderSupport.writeString(out, value.getSenderDeviceId());
        DirectSessionEncoderSupport.writeString(out, value.getSenderPublicKey());
        DirectSessionEncoderSupport.writeString(out, value.getConnectionNonce());
        DirectSessionEncoderSupport.writeString(out, value.getSignature());
        return out;
    }
}
