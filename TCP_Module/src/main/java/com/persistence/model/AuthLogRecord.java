package com.persistence.model;

import java.time.LocalDateTime;

/**
 * Author: LQH
 * Date: 2026-05-01
 * Purpose: 认证日志的实体类
 *
 * */

public class AuthLogRecord
{
    private String deviceId;
    private String publicKey;
    private String challengeId;
    private String clientIp;
    private String result;
    private String failureReason;
    private LocalDateTime createdAt;


    public AuthLogRecord(String challengeId, String clientIp, LocalDateTime createdAt, String deviceId, String failureReason, String publicKey, String result) {
        this.challengeId = challengeId;
        this.clientIp = clientIp;
        this.createdAt = createdAt;
        this.deviceId = deviceId;
        this.failureReason = failureReason;
        this.publicKey = publicKey;
        this.result = result;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    @Override
    public String toString() {
        return "AuthLogRecord{" +
                "challengeId='" + challengeId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", result='" + result + '\'' +
                ", failureReason='" + failureReason + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
