package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.redis-state")
public class RedisStateProperties
{
    private long challengeTtlSeconds;
    private long onlineSessionTtlSeconds;
    private long transferRouteTtlSeconds;

    public long getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(long challengeTtlSeconds) {
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    public long getOnlineSessionTtlSeconds() {
        return onlineSessionTtlSeconds;
    }

    public void setOnlineSessionTtlSeconds(long onlineSessionTtlSeconds) {
        this.onlineSessionTtlSeconds = onlineSessionTtlSeconds;
    }

    public long getTransferRouteTtlSeconds() {
        return transferRouteTtlSeconds;
    }

    public void setTransferRouteTtlSeconds(long transferRouteTtlSeconds) {
        this.transferRouteTtlSeconds = transferRouteTtlSeconds;
    }

    @Override
    public String toString() {
        return "RedisStateProperties{" +
                "challengeTtlSeconds=" + challengeTtlSeconds +
                ", onlineSessionTtlSeconds=" + onlineSessionTtlSeconds +
                ", transferRouteTtlSeconds=" + transferRouteTtlSeconds +
                '}';
    }
}
