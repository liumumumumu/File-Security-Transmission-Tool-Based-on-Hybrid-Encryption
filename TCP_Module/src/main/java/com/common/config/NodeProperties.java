package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "node")
public class NodeProperties
{
    private String deviceId;
    private boolean autoConnect;

    public boolean isAutoConnect() {
        return autoConnect;
    }

    public void setAutoConnect(boolean autoConnect) {
        this.autoConnect = autoConnect;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "NodeProperties{" +
                "autoConnect=" + autoConnect +
                ", deviceId='" + deviceId + '\'' +
                '}';
    }
}
