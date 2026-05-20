package com.common.codec.decoder.ProtocolDecodingLayer.direct;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.direct.DirectSessionHelloPacket;
import io.netty.buffer.ByteBuf;

public class DirectSessionHelloDecoder extends Decoder
{
    @Override
    public DirectSessionHelloPacket decode(ByteBuf in)
    {
        byte type = in.readByte();
        if(type != MessageType.Direct_Session_Hello)
        {
            throw new IllegalStateException("Decoder misuse for direct hello");
        }
        return new DirectSessionHelloPacket(
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in)
        );
    }
}
