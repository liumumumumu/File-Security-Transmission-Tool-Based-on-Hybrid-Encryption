package com.common.protocol.auth;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class ChallengePacket extends Packet
{
    //登陆验证的Challeng数据包
    private String challengeId;//服务器可能同时处理多个challenge,并需要管理它们的状态，所以需要challengeId
    private String challenge;

    public ChallengePacket(String challenge, String challengeId) {
        this.challenge = challenge;
        this.challengeId = challengeId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Challenge;
    }

    @Override
    public String toString() {
        return "ChallengePacket{" +
                "challenge='" + challenge + '\'' +
                ", challengeId='" + challengeId + '\'' +
                '}';
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }
}
