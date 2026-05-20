package com.common.codec.encoder.direct;

import io.netty.buffer.ByteBuf;

public class DirectSessionEncoderSupport
{
    private DirectSessionEncoderSupport()
    {
    }

    static void writeString(ByteBuf out, String value)
    {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }
}
