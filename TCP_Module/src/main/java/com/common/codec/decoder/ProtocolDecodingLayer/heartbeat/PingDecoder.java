package com.common.codec.decoder.ProtocolDecodingLayer.heartbeat;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.heartbeat.PingPacket;
import io.netty.buffer.ByteBuf;

public class PingDecoder extends Decoder
{
    @Override
    public PingPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Ping)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by PingDecoder");
        }

        return new PingPacket();
    }
}
