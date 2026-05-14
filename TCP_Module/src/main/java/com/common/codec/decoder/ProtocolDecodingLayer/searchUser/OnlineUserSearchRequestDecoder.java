package com.common.codec.decoder.ProtocolDecodingLayer.searchUser;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class OnlineUserSearchRequestDecoder extends Decoder
{
    @Override
    public OnlineUserSearchRequestPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Onlie_User_Search_Request)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by OnlineUserSearchRequestDecoder");
        }

        String requestId = readString(in);
        String accountId = readString(in);
        return new OnlineUserSearchRequestPacket(accountId, requestId);
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
