package com.client.direct;

import com.client.direct.qr.ReceiverResponseQr;
import com.client.transport.PacketTransport;

public class DirectSessionInfo
{
    private String inviteId;
    private String sessionId;
    private String peerAccountId;
    private String peerDeviceId;
    private String peerPublicKey;
    private PacketTransport transport;
    private ReceiverResponseQr receiverResponse;


    public DirectSessionInfo() {}

    public DirectSessionInfo(String inviteId,
                             String sessionId,
                             String peerAccountId,
                             String peerDeviceId,
                             String peerPublicKey,
                             PacketTransport transport,
                             ReceiverResponseQr receiverResponse) {
        this.inviteId = inviteId;
        this.peerAccountId = peerAccountId;
        this.peerDeviceId = peerDeviceId;
        this.peerPublicKey = peerPublicKey;
        this.receiverResponse = receiverResponse;
        this.sessionId = sessionId;
        this.transport = transport;
    }

    public String getInviteId() {
        return inviteId;
    }

    public void setInviteId(String inviteId) {
        this.inviteId = inviteId;
    }

    public String getPeerAccountId() {
        return peerAccountId;
    }

    public void setPeerAccountId(String peerAccountId) {
        this.peerAccountId = peerAccountId;
    }

    public String getPeerDeviceId() {
        return peerDeviceId;
    }

    public void setPeerDeviceId(String peerDeviceId) {
        this.peerDeviceId = peerDeviceId;
    }

    public String getPeerPublicKey() {
        return peerPublicKey;
    }

    public void setPeerPublicKey(String peerPublicKey) {
        this.peerPublicKey = peerPublicKey;
    }

    public ReceiverResponseQr getReceiverResponse() {
        return receiverResponse;
    }

    public void setReceiverResponse(ReceiverResponseQr receiverResponse) {
        this.receiverResponse = receiverResponse;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public PacketTransport getTransport() {
        return transport;
    }

    public void setTransport(PacketTransport transport) {
        this.transport = transport;
    }

    @Override
    public String toString() {
        return "DirectSessionInfo{" +
                "inviteId='" + inviteId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", peerAccountId='" + peerAccountId + '\'' +
                ", peerDeviceId='" + peerDeviceId + '\'' +
                ", peerPublicKey='" + peerPublicKey + '\'' +
                ", transport=" + transport +
                ", receiverResponse=" + receiverResponse +
                '}';
    }
}
