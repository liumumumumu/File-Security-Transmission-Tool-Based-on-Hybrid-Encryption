package com.controller.dto;

public class ImportPrivateKeyRequest
{
    private String privateKey;
    private String privateKeyPath;

    public ImportPrivateKeyRequest(String privateKey, String privateKeyPath) {
        this.privateKey = privateKey;
        this.privateKeyPath = privateKeyPath;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    @Override
    public String toString() {
        return "ImportPrivateKeyRequest{" +
                "privateKey='" + privateKey + '\'' +
                ", privateKeyPath='" + privateKeyPath + '\'' +
                '}';
    }
}
