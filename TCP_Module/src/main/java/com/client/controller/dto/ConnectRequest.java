package com.client.controller.dto;

public class ConnectRequest
{
    private String host;
    private Integer port;

    public ConnectRequest() {
    }

    public ConnectRequest(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return "ConnectRequest{" +
                "host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
