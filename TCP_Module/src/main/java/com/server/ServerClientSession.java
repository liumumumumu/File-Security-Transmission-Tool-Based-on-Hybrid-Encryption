package com.server;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

public class ServerClientSession {
    private String deviceId;
    private String publicKey;
    private Channel channel;

    public ServerClientSession(String deviceId, String publicKey, Channel channel) {
        this.deviceId = deviceId;
        this.publicKey = publicKey;
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
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
        return "ServerClientSession{" +
                "channel=" + channel +
                ", deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                '}';
    }


}
