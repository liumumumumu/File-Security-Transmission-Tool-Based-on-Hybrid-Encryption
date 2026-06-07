package com.common.codec.decoder.ProtocolDecodingLayer.message;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import io.netty.buffer.ByteBuf;

public class TextMessageReadReceiptDecoder extends Decoder
{
    @Override
    public TextMessageReadReceiptPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Text_Message_Read_Receipt)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by TextMessageReadReceiptDecoder");
        }
        return new TextMessageReadReceiptPacket(
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in),
                TextMessageDecoderSupport.readString(in)
        );
    }
}
