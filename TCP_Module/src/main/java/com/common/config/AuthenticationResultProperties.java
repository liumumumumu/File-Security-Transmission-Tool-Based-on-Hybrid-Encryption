package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "authentication-result")
public class AuthenticationResultProperties
{
    private String succeed = "Authentication Passed";
    private String failed = "Authentication Failed";

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
}
