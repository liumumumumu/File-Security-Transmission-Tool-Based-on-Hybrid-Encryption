package com.common.codec.decoder.ProtocolDecodingLayer.direct;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.direct.DirectSessionChallengePacket;
import io.netty.buffer.ByteBuf;

public class DirectSessionChallengeDecoder extends Decoder
{
    @Override
    public DirectSessionChallengePacket decode(ByteBuf in)
    {
        byte type = in.readByte();
        if(type != MessageType.Direct_Session_Challenge)
        {
            throw new IllegalStateException("Decoder misuse for direct challenge");
        }
        return new DirectSessionChallengePacket(
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in)
        );
    }
}
