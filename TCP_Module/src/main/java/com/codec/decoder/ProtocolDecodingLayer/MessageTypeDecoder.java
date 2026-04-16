package com.codec.decoder.ProtocolDecodingLayer;

import io.netty.buffer.ByteBuf;

public final class MessageTypeDecoder
{
    public static final byte MessageTypeDecode(ByteBuf in)
    {
        return in.readByte();
    }
}
