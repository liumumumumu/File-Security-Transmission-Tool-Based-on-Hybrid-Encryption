package com.codec.decoder.ProtocolDecodingLayer;

import com.protocol.Packet;
import io.netty.buffer.ByteBuf;

public abstract class Decoder
{
    public abstract Packet decode(ByteBuf in);
}
