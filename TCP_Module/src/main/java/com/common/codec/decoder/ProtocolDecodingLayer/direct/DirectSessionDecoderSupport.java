package com.common.codec.decoder.ProtocolDecodingLayer.direct;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class DirectSessionDecoderSupport
{
    private DirectSessionDecoderSupport()
    {
    }

    static String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
