package com.server;

import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-04-27
 * Purpose: 等待客户端完成签名验证的认证挑战信息
 *
 * */

public class PendingAuthChallenge
{
    private String deviceId;
    private String publicKey;
    private String challengeId;
    private String challenge;

    public PendingAuthChallenge(String deviceId, String publicKey, String challengeId, String challenge) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
        this.challengeId = challengeId;
        this.challenge = challenge;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return "PendingAuthChallenge{" +
                "challenge='" + challenge + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", challengeId='" + challengeId + '\'' +
                '}';
    }
}
