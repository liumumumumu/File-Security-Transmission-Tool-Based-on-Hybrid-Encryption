package com.common.protocol.searchUser;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class OnlineUserSearchRequestPacket extends Packet
{
    private final String requestId;
    private final String accountId;

    public OnlineUserSearchRequestPacket(String accountId, String requestId) {
        this.accountId = accountId;
        this.requestId = requestId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Onlie_User_Search_Request;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getRequestId() {
        return requestId;
    }

    @Override
    public String toString() {
        return "OnlineUserSearchRequestPacket{" +
                "accountId='" + accountId + '\'' +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}
