package com.common.codec.decoder.ProtocolDecodingLayer.message;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.message.TextMessagePacket;
import io.netty.buffer.ByteBuf;

public class TextMessageDecoder extends Decoder
{
    @Override
    public TextMessagePacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Text_Message)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by TextMessageDecoder");
        }
        return new TextMessagePacket(
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readBytes(in),
                TextMessageDecoderSupport.readBytes(in),
                TextMessageDecoderSupport.readBytes(in)
        );
    }
}
