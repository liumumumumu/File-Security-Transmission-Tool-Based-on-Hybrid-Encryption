package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.local-storage")
public class LocalStorageProperties
{
    private String transferHistoryPath;
    private String sqlitePath;
    private String deviceIdPath;

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

    @Override
    public String toString() {
        return "LocalStorageProperties{" +
                "sqlitePath='" + sqlitePath + '\'' +
                ", deviceIdPath='" + deviceIdPath + '\'' +
                ", transferHistoryPath='" + transferHistoryPath + '\'' +
                '}';
    }
}
