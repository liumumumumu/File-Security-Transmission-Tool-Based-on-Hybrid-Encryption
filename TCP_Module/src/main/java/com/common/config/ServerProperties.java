package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "server.tcp")
public class ServerProperties
{
    private boolean enabled;
    private String bindHost = "0.0.0.0";
    private int bindPort = 9000;

    public String getBindHost() {
        return bindHost;
    }

    public void setBindHost(String bindHost) {
        this.bindHost = bindHost;
    }

    public int getBindPort() {
        return bindPort;
    }

    public void setBindPort(int bindPort) {
        this.bindPort = bindPort;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "ServerProperties{" +
                "bindHost='" + bindHost + '\'' +
                ", enabled=" + enabled +
                ", bindPort=" + bindPort +
                '}';
    }
}
