package com.common.protocol.auth;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class AuthResponsePacket extends Packet
{
    private String publicKey;
    private String challengeId;
    private String signature;

    public AuthResponsePacket(String challengeId, String publicKey, String signature) {
        this.challengeId = challengeId;
        this.publicKey = publicKey;
        this.signature = signature;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Auth_Response;
    }

    @Override
    public String toString() {
        return "AuthResponsePacket{" +
                "challengeId='" + challengeId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }
}
