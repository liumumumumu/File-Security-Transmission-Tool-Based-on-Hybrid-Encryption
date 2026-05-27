package com.common.codec.encoder.message;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.message.TextMessagePacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TextMessageEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof TextMessagePacket message))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type TextMessagePacket");
        }
        ByteBuf out = Unpooled.buffer();
        out.writeByte(message.getMessageType());
        TextMessageCodecSupport.writeString(out, message.getMessageId());
        TextMessageCodecSupport.writeString(out, message.getSenderAccountId());
        TextMessageCodecSupport.writeString(out, message.getSenderPublicKey());
        TextMessageCodecSupport.writeString(out, message.getReceiverAccountId());
        TextMessageCodecSupport.writeString(out, message.getReceiverPublicKey());
        TextMessageCodecSupport.writeString(out, message.getCreatedAt());
        TextMessageCodecSupport.writeString(out, message.getEncryptedSessionKey());
        TextMessageCodecSupport.writeBytes(out, message.getNonce());
        TextMessageCodecSupport.writeBytes(out, message.getCiphertext());
        TextMessageCodecSupport.writeBytes(out, message.getTag());
        return out;
    }
}
