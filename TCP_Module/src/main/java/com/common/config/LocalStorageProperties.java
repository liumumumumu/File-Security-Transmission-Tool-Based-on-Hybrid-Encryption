package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Author: LQH
 * Date: 2026-05-13
 * Purpose: 配置绑定类，读取配置文件里面的本地存储路径配置
 *
 **/

@ConfigurationProperties(prefix = "app.local-storage")
public class LocalStorageProperties
{
    private String transferHistoryPath;//传输历史记录文件的路径
    private String sqlitePath;//SQLite数据库的路径
    private String deviceIdPath;//本机设备ID保存路径
    private String startupStatePath;//启动状态文件路径

    public String getTransferHistoryPath() {
        return transferHistoryPath;
    }

    public void setTransferHistoryPath(String transferHistoryPath) {
        this.transferHistoryPath = transferHistoryPath;
    }

    public String getSqlitePath() {
        return sqlitePath;
    }

    public void setSqlitePath(String sqlitePath) {
        this.sqlitePath = sqlitePath;
    }

    public String getDeviceIdPath() {
        return deviceIdPath;
    }

    public void setDeviceIdPath(String deviceIdPath) {
        this.deviceIdPath = deviceIdPath;
    }

    public String getStartupStatePath() {
        return startupStatePath;
    }

    public void setStartupStatePath(String startupStatePath) {
        this.startupStatePath = startupStatePath;
    }

    @Override
    public String toString() {
        return "LocalStorageProperties{" +
                "sqlitePath='" + sqlitePath + '\'' +
                ", deviceIdPath='" + deviceIdPath + '\'' +
                ", startupStatePath='" + startupStatePath + '\'' +
                ", transferHistoryPath='" + transferHistoryPath + '\'' +
                '}';
    }
}
