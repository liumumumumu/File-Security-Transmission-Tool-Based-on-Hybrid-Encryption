package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "transfer")
public class TransferProperties
{
    private int chunkSizeBytes;
    private String receiveDir;

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public String getReceiveDir() {
        return receiveDir;
    }

    public void setReceiveDir(String receiveDir) {
        this.receiveDir = receiveDir;
    }

    @Override
    public String toString() {
        return "TransferProperties{" +
                "chunkSizeBytes=" + chunkSizeBytes +
                ", receiveDir='" + receiveDir + '\'' +
                '}';
    }
}
