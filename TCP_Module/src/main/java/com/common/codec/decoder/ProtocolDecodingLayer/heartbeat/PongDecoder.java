package com.common.codec.decoder.ProtocolDecodingLayer.heartbeat;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.heartbeat.PongPacket;
import io.netty.buffer.ByteBuf;

public class PongDecoder extends Decoder
{
    @Override
    public PongPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Pong)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by PongDecoder");
        }

        return new PongPacket();
    }
}
