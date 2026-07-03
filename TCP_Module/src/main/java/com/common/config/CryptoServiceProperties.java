package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Author: LQH
 * Date: 2026-04-26
 * Update: 2026-07-03 — 新增 keyDir 字段，私钥文件加密存储后不再依赖外部 Python 服务
 * Purpose: crypto-service 配置属性绑定 (address/port 保留兼容, keyDir 为新增密钥存储路径)
 */
@ConfigurationProperties(prefix = "crypto-service")
public class CryptoServiceProperties
{
    private String address;
    private int port;
    private String keyDir;

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

    public String getKeyDir()
    {
        return keyDir;
    }

    public void setKeyDir(String keyDir)
    {
        this.keyDir = keyDir;
    }

    @Override
    public String toString() {
        return "CryptoServiceProperties{" +
                "address='" + address + '\'' +
                ", port=" + port +
                ", keyDir='" + keyDir + '\'' +
                '}';
    }
}
