package com.codec.encoder;

import com.protocol.Packet;
import io.netty.buffer.ByteBuf;

import java.io.IOException;

public abstract class Encoder
{
    public abstract ByteBuf encode(Packet packet);
}
