package com.persistence.model;

import java.time.LocalDateTime;

/**
 * Author: LQH
 * Date: 2026-05-01
 * Purpose: 设备记录的实体类
 *
 * */

public class DeviceRecord
{
    private String deviceId;
    private String publicKey;
    private String status;
    private LocalDateTime lastSeenAt;

    public DeviceRecord(String deviceId, LocalDateTime lastSeenAt, String publicKey, String status) {
        this.deviceId = deviceId;
        this.lastSeenAt = lastSeenAt;
        this.publicKey = publicKey;
        this.status = status;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "DeviceRecord{" +
                "deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", status='" + status + '\'' +
                ", lastSeenAt=" + lastSeenAt +
                '}';
    }
}
