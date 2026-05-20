package com.common.protocol.direct;

import com.common.protocol.Packet;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
IPv6直连模式下，接收方返回给发送方的最终确认包(第4个)
 */

public class DirectSessionAcceptedPacket extends Packet
{
    private final String inviteId;
    private final String sessionId;
    private final boolean accepted;
    private final String message;
    private final String receiverAccountId;
    private final String receiverDeviceId;
    private final String receiverPublicKey;

    public DirectSessionAcceptedPacket(String inviteId, String sessionId, boolean accepted, String message, String receiverAccountId, String receiverDeviceId, String receiverPublicKey) {
        this.inviteId = inviteId;
        this.sessionId = sessionId;
        this.accepted = accepted;
        this.message = message;
        this.receiverAccountId = receiverAccountId;
        this.receiverDeviceId = receiverDeviceId;
        this.receiverPublicKey = receiverPublicKey;
    }

    @Override
    public byte getMessageType() {
        return MessageType.Direct_Session_Accepted;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getMessage() {
        return message;
    }

    public String getReceiverAccountId() {
        return receiverAccountId;
    }

    public String getReceiverDeviceId() {
        return receiverDeviceId;
    }

    public String getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return "DirectSessionAcceptedPacket{" +
                "accepted=" + accepted +
                ", inviteId='" + inviteId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", message='" + message + '\'' +
                ", receiverAccountId='" + receiverAccountId + '\'' +
                ", receiverDeviceId='" + receiverDeviceId + '\'' +
                ", receiverPublicKey='" + receiverPublicKey + '\'' +
                '}';
    }
}
