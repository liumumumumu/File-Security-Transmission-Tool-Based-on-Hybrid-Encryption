package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "client")
public class ClientProperties
{
    private String serverHost = "127.0.0.1";
    private int serverPort = 9000;

    public String getServerHost()
    {
        return serverHost;
    }

    public void setServerHost(String serverHost)
    {
        this.serverHost = serverHost;
    }

    public int getServerPort()
    {
        return serverPort;
    }

    public void setServerPort(int serverPort)
    {
        this.serverPort = serverPort;
    }
}
