package com.codec.decoder.ProtocolDecodingLayer.heartbeat;

import com.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.protocol.MessageType;
import com.protocol.heartbeat.PingPacket;
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
