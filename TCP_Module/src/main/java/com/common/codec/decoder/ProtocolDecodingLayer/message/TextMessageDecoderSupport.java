package com.common.codec.decoder.ProtocolDecodingLayer.message;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 消息协议解码器的内部工具类，用来复用从ByteBuf里读取长度前缀字段的逻辑
 *
 * */

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
