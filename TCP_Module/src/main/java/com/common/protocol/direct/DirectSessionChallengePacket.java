package com.common.protocol.direct;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
直连模式下的，Challenge包，证明发送方是否持有对应的私钥(第2个)
 */

public class DirectSessionChallengePacket extends Packet
{
    private final String inviteId;
    private final String sessionId;
    private final String challenge;

    public DirectSessionChallengePacket(String inviteId, String sessionId, String challenge) {
        this.inviteId = inviteId;
        this.sessionId = sessionId;
        this.challenge = challenge;
    }

    @Override
    public byte getMessageType() {
        return MessageType.Direct_Session_Challenge;
    }

    public String getChallenge() {
        return challenge;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String toString() {
        return "DirectSessionChallengePacket{" +
                "challenge='" + challenge + '\'' +
                ", inviteId='" + inviteId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                '}';
    }
}
