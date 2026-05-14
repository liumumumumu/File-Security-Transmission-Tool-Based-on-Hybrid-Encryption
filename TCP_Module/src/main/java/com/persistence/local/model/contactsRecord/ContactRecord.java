package com.persistence.local.model.contactsRecord;

/**
 * Author: LQH
 * Date: 2026-05-08
 * Purpose: 联系人列表的实体类
 *
 * */

public class ContactRecord
{
    private long id;
    private int contactIndex;
    private String alias;   //联系人备注名
    private String accountId;
    private String publicKey;
    private String createdAt;
    private String updatedAt;

    public ContactRecord() {}

    public ContactRecord(String accountId, String alias, int contactIndex, String createdAt, long id, String publicKey, String updatedAt) {
        this.accountId = accountId;
        this.alias = alias;
        this.contactIndex = contactIndex;
        this.createdAt = createdAt;
        this.id = id;
        this.publicKey = publicKey;
        this.updatedAt = updatedAt;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public int getContactIndex() {
        return contactIndex;
    }

    public void setContactIndex(int contactIndex) {
        this.contactIndex = contactIndex;
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

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ContactRecord{" +
                "accountId='" + accountId + '\'' +
                ", id=" + id +
                ", contactIndex=" + contactIndex +
                ", alias='" + alias + '\'' +
                ", publicKey='" + publicKey + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}
