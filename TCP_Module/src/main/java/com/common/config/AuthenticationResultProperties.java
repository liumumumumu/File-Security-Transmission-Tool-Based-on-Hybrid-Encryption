package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "authentication-result")
public class AuthenticationResultProperties
{
    private String succeed;
    private String failed;

    public String getSucceed()
    {
        return succeed;
    }

    public void setSucceed(String succeed)
    {
        this.succeed = succeed;
    }

    public String getFailed()
    {
        return failed;
    }

    public void setFailed(String failed)
    {
        this.failed = failed;
    }

    @Override
    public String toString() {
        return "AuthenticationResultProperties{" +
                "failed='" + failed + '\'' +
                ", succeed='" + succeed + '\'' +
                '}';
    }
}
