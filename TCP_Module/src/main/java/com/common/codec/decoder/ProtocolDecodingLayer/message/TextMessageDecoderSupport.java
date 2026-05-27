package com.common.codec.decoder.ProtocolDecodingLayer.message;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class TextMessageDecoderSupport
{
    private TextMessageDecoderSupport() {}

    static String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte[] readBytes(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return bytes;
    }
}
