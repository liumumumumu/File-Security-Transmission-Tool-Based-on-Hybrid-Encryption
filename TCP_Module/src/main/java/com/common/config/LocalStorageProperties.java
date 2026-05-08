package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.local-storage")
public class LocalStorageProperties
{
    private String transferHistoryPath;
    private String sqlitePath;

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

    @Override
    public String toString() {
        return "LocalStorageProperties{" +
                "sqlitePath='" + sqlitePath + '\'' +
                ", transferHistoryPath='" + transferHistoryPath + '\'' +
                '}';
    }
}
