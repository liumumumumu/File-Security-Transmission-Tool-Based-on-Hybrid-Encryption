package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 直连模型配置文件读取类
 *
 * */

@ConfigurationProperties(prefix = "direct")
public class DirectProperties
{
    private int defaultFixedListenPort;

    public int getDefaultFixedListenPort() {
        return defaultFixedListenPort;
    }

    public void setDefaultFixedListenPort(int defaultFixedListenPort) {
        this.defaultFixedListenPort = defaultFixedListenPort;
    }
}
