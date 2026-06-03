package com.common.codec.encoder.message;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

final class TextMessageCodecSupport
{
    private TextMessageCodecSupport() {}

    static void writeString(ByteBuf out, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    static void writeBytes(ByteBuf out, byte[] value)
    {
        byte[] bytes = value == null ? new byte[0] : value;
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }
}
