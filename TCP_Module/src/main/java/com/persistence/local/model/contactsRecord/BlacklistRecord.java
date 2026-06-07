package com.persistence.local.model.contactsRecord;

/**
 * Author: LQH
 * Date: 2026-05-08
 * Purpose: 黑名单列表的实体类
 *
 * */


public class BlacklistRecord
{
    private long id;
    private String accountId;
    private String publicKey;
    private String reason;
    private String createdAt;

    public BlacklistRecord() {}

    public BlacklistRecord(String accountId, String createdAt, long id, String publicKey, String reason) {
        this.accountId = accountId;
        this.createdAt = createdAt;
        this.id = id;
        this.publicKey = publicKey;
        this.reason = reason;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "BlacklistRecord{" +
                "accountId='" + accountId + '\'' +
                ", id=" + id +
                ", publicKey='" + publicKey + '\'' +
                ", reason='" + reason + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
