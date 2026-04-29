package com.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crypto-key")
public class CryptoKeyProperties
{
    private String privateKeyPath = "${user.home}/.file-security-transmission/identity_private.pkcs8";
    private String publicKeyPath = "${user.home}/.file-security-transmission/identity_public.x509";

    public String getPrivateKeyPath()
    {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath)
    {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath()
    {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath)
    {
        this.publicKeyPath = publicKeyPath;
    }
}
