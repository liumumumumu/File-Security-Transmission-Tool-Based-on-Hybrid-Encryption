package com.common.protocol.direct;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
发送方收到Challenge后，用自己的私钥给这个Challenge签名，并将签名结果发回给接收方(第3个)
 */

public class DirectSessionProofPacket extends Packet
{
    private final String inviteId;
    private final String sessionId;
    private final String signature;

    public DirectSessionProofPacket(String inviteId, String sessionId, String signature) {
        this.inviteId = inviteId;
        this.sessionId = sessionId;
        this.signature = signature;
    }

    @Override
    public byte getMessageType() {
        return MessageType.Direct_Session_Proof;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public String toString() {
        return "DirectSessionProofPacket{" +
                "inviteId='" + inviteId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
