package com.common.codec.decoder.ProtocolDecodingLayer.direct;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.direct.DirectSessionProofPacket;
import io.netty.buffer.ByteBuf;

public class DirectSessionProofDecoder extends Decoder
{
    @Override
    public DirectSessionProofPacket decode(ByteBuf in)
    {
        byte type = in.readByte();
        if(type != MessageType.Direct_Session_Proof)
        {
            throw new IllegalStateException("Decoder misuse for direct proof");
        }
        return new DirectSessionProofPacket(
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in)
        );
    }
}
