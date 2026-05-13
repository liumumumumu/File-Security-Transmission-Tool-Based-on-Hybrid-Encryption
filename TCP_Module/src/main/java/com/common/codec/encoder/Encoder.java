package com.common.codec.encoder;

import com.common.protocol.Packet;
import io.netty.buffer.ByteBuf;

public abstract class Encoder
{
    public abstract ByteBuf encode(Packet packet);
}
