package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "client")
public class ClientProperties
{
    private String serverHost;
    private int serverPort;
    private int connectionTimeout;//ms
    private int authTimeoutSeconds;
    private int ackTimeoutSeconds;

    public int getAckTimeoutSeconds() {
        return ackTimeoutSeconds;
    }

    public void setAckTimeoutSeconds(int ackTimeoutSeconds) {
        this.ackTimeoutSeconds = ackTimeoutSeconds;
    }

    public int getAuthTimeoutSeconds() {
        return authTimeoutSeconds;
    }

    public void setAuthTimeoutSeconds(int authTimeoutSeconds) {
        this.authTimeoutSeconds = authTimeoutSeconds;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public String getServerHost() {
        return serverHost;
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    @Override
    public String toString() {
        return "ClientProperties{" +
                "ackTimeoutSeconds=" + ackTimeoutSeconds +
                ", serverHost='" + serverHost + '\'' +
                ", serverPort=" + serverPort +
                ", connectionTimeout=" + connectionTimeout +
                ", authTimeoutSeconds=" + authTimeoutSeconds +
                '}';
    }
}
