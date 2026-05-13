package com.common.codec.encoder.searchUser;

import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

public class OnlineUserSearchResultEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof OnlineUserSearchResultPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type OnlineUserSearchResultPacket");
        }

        OnlineUserSearchResultPacket  onlineUserSearchResultPacket = (OnlineUserSearchResultPacket)packet;

        ByteBuf startFrame = Unpooled.buffer();

        startFrame.writeByte(packet.getMessageType());//写入数据包类型

        byte[] requestIdBytes=onlineUserSearchResultPacket.getRequestId().getBytes(CharsetUtil.UTF_8);
        startFrame.writeInt(requestIdBytes.length);
        startFrame.writeBytes(requestIdBytes);

        byte[] accountIdBytes=onlineUserSearchResultPacket.getAccountId().getBytes(CharsetUtil.UTF_8);
        startFrame.writeInt(accountIdBytes.length);
        startFrame.writeBytes(accountIdBytes);

        byte[] publicKeyBytes=safeString(onlineUserSearchResultPacket.getPublicKey()).getBytes(CharsetUtil.UTF_8);
        startFrame.writeInt(publicKeyBytes.length);
        startFrame.writeBytes(publicKeyBytes);

        startFrame.writeBoolean(onlineUserSearchResultPacket.isSearchResult());

        byte[] messageBytes=safeString(onlineUserSearchResultPacket.getMessage()).getBytes(CharsetUtil.UTF_8);
        startFrame.writeInt(messageBytes.length);
        startFrame.writeBytes(messageBytes);

        return startFrame;
    }

    private String safeString(String value)
    {
        return value == null ? "" : value;
    }
}
