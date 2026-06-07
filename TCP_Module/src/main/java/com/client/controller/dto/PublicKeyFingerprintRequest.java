package com.client.controller.dto;

public class PublicKeyFingerprintRequest
{
    private String publicKey;

    public PublicKeyFingerprintRequest() {
    }

    public PublicKeyFingerprintRequest(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
