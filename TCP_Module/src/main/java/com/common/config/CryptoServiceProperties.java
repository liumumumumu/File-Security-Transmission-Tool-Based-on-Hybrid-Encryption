package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "crypto-service")
public class CryptoServiceProperties
{
    private String address;
    private int port;

    public String getAddress()
    {
        return address;
    }

    public void setAddress(String address)
    {
        this.address = address;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    @Override
    public String toString() {
        return "CryptoServiceProperties{" +
                "address='" + address + '\'' +
                ", port=" + port +
                '}';
    }
}
