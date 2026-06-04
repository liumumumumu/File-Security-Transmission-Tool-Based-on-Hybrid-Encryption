package com.common.codec.encoder.message;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class TextMessageReadReceiptEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof TextMessageReadReceiptPacket receipt))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type TextMessageReadReceiptPacket");
        }
        ByteBuf out = Unpooled.buffer();
        out.writeByte(receipt.getMessageType());
        TextMessageCodecSupport.writeString(out, receipt.getMessageId());
        TextMessageCodecSupport.writeString(out, receipt.getSenderAccountId());
        TextMessageCodecSupport.writeString(out, receipt.getReaderAccountId());
        TextMessageCodecSupport.writeString(out, receipt.getReaderDeviceId());
        TextMessageCodecSupport.writeString(out, receipt.getReadAt());
        return out;
    }
}
