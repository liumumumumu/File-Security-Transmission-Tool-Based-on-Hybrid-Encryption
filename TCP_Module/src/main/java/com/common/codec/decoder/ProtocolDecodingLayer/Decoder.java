package com.common.codec.decoder.ProtocolDecodingLayer;

import com.common.protocol.Packet;
import io.netty.buffer.ByteBuf;

public abstract class Decoder
{
    public abstract Packet decode(ByteBuf in);
}
