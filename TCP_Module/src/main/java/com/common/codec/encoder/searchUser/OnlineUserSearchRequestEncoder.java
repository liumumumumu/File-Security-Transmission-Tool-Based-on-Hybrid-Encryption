package com.common.codec.encoder.searchUser;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class OnlineUserSearchRequestEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(! (packet instanceof OnlineUserSearchRequestPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type OnlineUserSearchRequestPacket");
        }

        OnlineUserSearchRequestPacket  OUSRPacket = (OnlineUserSearchRequestPacket) packet;

        ByteBuf startFrame = Unpooled.buffer();

        startFrame.writeByte(packet.getMessageType());//写入数据包类型

        byte[] requestIdBytes=OUSRPacket.getRequestId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(requestIdBytes.length);
        startFrame.writeBytes(requestIdBytes);

        byte[] accountIdBytes=OUSRPacket.getAccountId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(accountIdBytes.length);
        startFrame.writeBytes(accountIdBytes);

        return startFrame;
    }
}
