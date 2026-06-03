package com.common.codec.encoder.message;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.message.TextMessageAckPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TextMessageAckEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof TextMessageAckPacket ack))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type TextMessageAckPacket");
        }
        ByteBuf out = Unpooled.buffer();
        out.writeByte(ack.getMessageType());
        TextMessageCodecSupport.writeString(out, ack.getMessageId());
        out.writeBoolean(ack.isSuccess());
        TextMessageCodecSupport.writeString(out, ack.getMessage());
        TextMessageCodecSupport.writeString(out, ack.getSenderAccountId());
        TextMessageCodecSupport.writeString(out, ack.getReceiverAccountId());
        TextMessageCodecSupport.writeString(out, ack.getAckDeviceId());
        TextMessageCodecSupport.writeString(out, ack.getAckAt());
        return out;
    }
}
