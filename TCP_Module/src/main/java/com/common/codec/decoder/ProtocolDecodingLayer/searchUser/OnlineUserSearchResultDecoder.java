package com.common.codec.decoder.ProtocolDecodingLayer.searchUser;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class OnlineUserSearchResultDecoder extends Decoder
{
    @Override
    public OnlineUserSearchResultPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Onlie_User_Search_Result)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by OnlineUserSearchResultDecoder");
        }

        String requestId = readString(in);
        String accountId = readString(in);
        String publicKey = blankToNull(readString(in));
        boolean searchResult = in.readBoolean();
        String message = readString(in);
        return new OnlineUserSearchResultPacket(accountId, requestId, publicKey, searchResult, message);
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value;
    }
}
