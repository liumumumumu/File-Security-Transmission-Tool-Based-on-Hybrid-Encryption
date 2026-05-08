package com.common.protocol.searchUser;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class OnlineUserSearchResultPacket extends Packet
{
    private final String requestId;
    private final String accountId;
    private final String publicKey;


    private final boolean searchResult;
    private final String message;

    public OnlineUserSearchResultPacket(String accountId, String requestId, String publicKey, boolean searchResult, String message) {
        this.accountId = accountId;
        this.requestId = requestId;
        this.publicKey = publicKey;
        this.searchResult = searchResult;
        this.message = message;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Onlie_User_Search_Result;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getMessage() {
        return message;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getRequestId() {
        return requestId;
    }

    public boolean isSearchResult() {
        return searchResult;
    }

    @Override
    public String toString() {
        return "OnlineUserSearchResultPacket{" +
                "accountId='" + accountId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", searchResult=" + searchResult +
                ", message='" + message + '\'' +
                '}';
    }
}
