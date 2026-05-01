package com.server;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: 服务端内存里的已认证的客户端连接会话实体类
 *
 **/

public class ServerClientSession {
    private String deviceId;
    private String accountId;
    private String publicKey;
    private Channel channel;

    public ServerClientSession(String accountId, Channel channel, String deviceId, String publicKey) {
        this.accountId = accountId;
        this.channel = channel;
        this.deviceId = deviceId;
        this.publicKey = publicKey;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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
                "accountId='" + accountId + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", channel=" + channel +
                '}';
    }
}