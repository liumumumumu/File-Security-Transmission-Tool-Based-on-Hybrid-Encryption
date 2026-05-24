package com.common.codec.decoder.ProtocolDecodingLayer.direct;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.direct.DirectSessionAcceptedPacket;
import io.netty.buffer.ByteBuf;

public class DirectSessionAcceptedDecoder extends Decoder
{
    @Override
    public DirectSessionAcceptedPacket decode(ByteBuf in)
    {
        byte type = in.readByte();
        if(type != MessageType.Direct_Session_Accepted)
        {
            throw new IllegalStateException("Decoder misuse for direct accepted");
        }
        String inviteId = DirectSessionDecoderSupport.readString(in);
        String sessionId = DirectSessionDecoderSupport.readString(in);
        boolean accepted = in.readBoolean();
        return new DirectSessionAcceptedPacket(
                inviteId,
                sessionId,
                accepted,
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in),
                DirectSessionDecoderSupport.readString(in)
        );
    }
}
