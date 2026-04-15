package com.codec.encoder.auth;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.auth.AuthRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class AuthRequestEncoder extends Encoder
{
    @Override
    public ByteBuf  encode(Packet packet)
    {
        if(!(packet instanceof AuthRequestPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type AuthRequestPacket");
        }
        AuthRequestPacket ARPacket = (AuthRequestPacket) packet;

        ByteBuf startFrame = Unpooled.buffer();

        startFrame.writeByte(packet.getMessageType());//写入数据包类型

        //将String转换成byte数组
        byte[] publicKeyBytes=ARPacket.getPublicKey().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(publicKeyBytes.length);//写入byte数组的长度
        startFrame.writeBytes(publicKeyBytes);//写入byte数组本体

        byte[] deviceIdBytes=ARPacket.getDeviceId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(deviceIdBytes.length);
        startFrame.writeBytes(deviceIdBytes);
        return startFrame;
    }

}
