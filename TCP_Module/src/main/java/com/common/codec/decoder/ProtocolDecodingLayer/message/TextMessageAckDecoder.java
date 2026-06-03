package com.common.codec.decoder.ProtocolDecodingLayer.message;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.message.TextMessageAckPacket;
import io.netty.buffer.ByteBuf;

public class TextMessageAckDecoder extends Decoder
{
    @Override
    public TextMessageAckPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Text_Message_Ack)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by TextMessageAckDecoder");
        }
        String messageId = TextMessageDecoderSupport.readString(in);
        boolean success = in.readBoolean();
        return new TextMessageAckPacket(
                messageId,
                success,
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in)
        );
    }
}
