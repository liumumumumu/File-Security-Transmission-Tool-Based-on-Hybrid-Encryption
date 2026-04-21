package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.local-storage")
public class LocalStorageProperties
{
    private String transferHistoryPath;

    public String getTransferHistoryPath() {
        return transferHistoryPath;
    }

    public void setTransferHistoryPath(String transferHistoryPath) {
        this.transferHistoryPath = transferHistoryPath;
    }

    @Override
    public String toString() {
        return "LocalStorageProperties{" +
                "transferHistoryPath='" + transferHistoryPath + '\'' +
                '}';
    }
}
