package com.client.direct.qr;

import java.time.Instant;
import java.util.List;

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

public class ReceiverResponseQr
{
    private String invited;
    private String receiverAccountId;
    private String receiverDeviceId;
    private String receiverPublicKey;
    private Instant expiresAt;
    private List<String> ipv6AddressCandidates;//因为接收端设备通常不只有一个可尝试的IPv6地址。提高IPv6直连的成功率
    private int port;
    private String connectionNonce;
    private String signature;

    public ReceiverResponseQr() {}

    public ReceiverResponseQr(
                              String invited,
                              String receiverAccountId,
                              String receiverDeviceId,
                              String receiverPublicKey,
                              Instant expiresAt,
                              List<String> ipv6AddressCandidates,
                              int port,
                              String connectionNonce,
                              String signature) {
        this.connectionNonce = connectionNonce;
        this.expiresAt = expiresAt;
        this.invited = invited;
        this.ipv6AddressCandidates = ipv6AddressCandidates;
        this.port = port;
        this.receiverAccountId = receiverAccountId;
        this.receiverDeviceId = receiverDeviceId;
        this.receiverPublicKey = receiverPublicKey;
        this.signature = signature;
    }

    public String getConnectionNonce() {
        return connectionNonce;
    }

    public void setConnectionNonce(String connectionNonce) {
        this.connectionNonce = connectionNonce;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getInvited() {
        return invited;
    }

    public void setInvited(String invited) {
        this.invited = invited;
    }

    public List<String> getIpv6AddressCandidates() {
        return ipv6AddressCandidates;
    }

    public void setIpv6AddressCandidates(List<String> ipv6AddressCandidates) {
        this.ipv6AddressCandidates = ipv6AddressCandidates;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getReceiverAccountId() {
        return receiverAccountId;
    }

    public void setReceiverAccountId(String receiverAccountId) {
        this.receiverAccountId = receiverAccountId;
    }

    public String getReceiverDeviceId() {
        return receiverDeviceId;
    }

    public void setReceiverDeviceId(String receiverDeviceId) {
        this.receiverDeviceId = receiverDeviceId;
    }

    public String getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public void setReceiverPublicKey(String receiverPublicKey) {
        this.receiverPublicKey = receiverPublicKey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public String toString() {
        return "ReceiverResponseQr{" +
                "connectionNonce='" + connectionNonce + '\'' +
                ", invited='" + invited + '\'' +
                ", receiverAccountId='" + receiverAccountId + '\'' +
                ", receiverDeviceId='" + receiverDeviceId + '\'' +
                ", receiverPublicKey='" + receiverPublicKey + '\'' +
                ", expiresAt=" + expiresAt +
                ", ipv6AddressCandidates=" + ipv6AddressCandidates +
                ", port=" + port +
                ", signature='" + signature + '\'' +
                '}';
    }
}
