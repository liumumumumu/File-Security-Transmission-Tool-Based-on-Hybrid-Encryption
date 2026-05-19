package com.client.direct.qr;

import java.time.Instant;

/**
 * Author: LQH
 * Date: 2026-05-17
 * Purpose: 发送方发送给接收方的Qr code;
 * Explain: 在使用IPv6直连模式下，需要通过第三方平台交换公开信息;流程如下
 * 1.发送方将自己的信息(SenderOfferQr)发送给接收方
 * 2.接收方验证发送方的信息，并将自己的的信息(ReceiverResponseQr)发送给发送方
 * 3.发送方收到并验证ReceiverResponseQr后，发送文件传输请求FileOfferPacket给接收方，并等待接收方做出选择
 *
 * */

public class SenderOfferQr
{
    private String inviteId;
    private String senderAccountId;
    private String senderDeviceId;
    private String senderPublicKey;
    private Instant expiresAt;
    private String signature;

    public SenderOfferQr() {}

    public SenderOfferQr(
                         String inviteId,
                         String senderAccountId,
                         String senderDeviceId,
                         String senderPublicKey,
                         Instant expiresAt,
                         String signature) {
        this.expiresAt = expiresAt;
        this.inviteId = inviteId;
        this.senderAccountId = senderAccountId;
        this.senderDeviceId = senderDeviceId;
        this.senderPublicKey = senderPublicKey;
        this.signature = signature;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getInviteId() {
        return inviteId;
    }

    public void setInviteId(String inviteId) {
        this.inviteId = inviteId;
    }

    public String getSenderAccountId() {
        return senderAccountId;
    }

    public void setSenderAccountId(String senderAccountId) {
        this.senderAccountId = senderAccountId;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public void setSenderDeviceId(String senderDeviceId) {
        this.senderDeviceId = senderDeviceId;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "SenderOfferQr{" +
                "expiresAt=" + expiresAt +
                ", inviteId='" + inviteId + '\'' +
                ", senderAccountId='" + senderAccountId + '\'' +
                ", senderDeviceId='" + senderDeviceId + '\'' +
                ", senderPublicKey='" + senderPublicKey + '\'' +
                ", signature='" + signature + '\'' +
                '}';
    }
}
