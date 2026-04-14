package com.protocol.auth;

import com.protocol.MessageType;
import com.protocol.Packet;

public class AuthRequestPacket extends Packet
{
    private String publicKey;
    private String deviceId;//使用UUID标识用户的设备，逻辑设备ID，而不是硬件ID

    public AuthRequestPacket(String deviceId, String publicKey) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Auth_Request;
    }

    @Override
    public String toString() {
        return "AuthRequestPacket{" +
                "deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                '}';
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
}

