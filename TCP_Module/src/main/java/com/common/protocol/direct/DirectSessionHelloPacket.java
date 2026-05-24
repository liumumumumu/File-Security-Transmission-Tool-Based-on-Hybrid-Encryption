package com.common.protocol.direct;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
IPv6直连模式下，握手过程中的第一个数据包，直连邀请
 */

public class DirectSessionHelloPacket extends Packet
{
    private final String inviteId;
    private final String sessionId;
    private final String senderAccountId;
    private final String senderDeviceId;
    private final String senderPublicKey;
    private final String connectionNonce;
    private final String signature;

    public DirectSessionHelloPacket(String inviteId, String sessionId, String senderAccountId, String senderDeviceId, String senderPublicKey, String connectionNonce, String signature) {
        this.inviteId = inviteId;
        this.sessionId = sessionId;
        this.senderAccountId = senderAccountId;
        this.senderDeviceId = senderDeviceId;
        this.senderPublicKey = senderPublicKey;
        this.connectionNonce = connectionNonce;
        this.signature = signature;
    }

    @Override
    public byte getMessageType() {
        return MessageType.Direct_Session_Hello;
    }

    public String getConnectionNonce() {
        return connectionNonce;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getSenderAccountId() {
        return senderAccountId;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return "DirectSessionHelloPacket{" +
                "connectionNonce='" + connectionNonce + '\'' +
                ", inviteId='" + inviteId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", senderAccountId='" + senderAccountId + '\'' +
                ", senderDeviceId='" + senderDeviceId + '\'' +
                ", senderPublicKey='" + senderPublicKey + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
