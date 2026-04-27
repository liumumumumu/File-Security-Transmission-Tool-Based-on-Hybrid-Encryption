package com.server;

import org.springframework.stereotype.Component;

public class TransferRoute
{
    private String transferId;
    private String senderDeviceId;
    private String receiverDeviceId;

    public TransferRoute(String senderDeviceId, String transferId, String receiverDeviceId) {
        this.receiverDeviceId = receiverDeviceId;
        this.senderDeviceId = senderDeviceId;
        this.transferId = transferId;
    }

    public String getReceiverDeviceId() {
        return receiverDeviceId;
    }

    public void setReceiverDeviceId(String receiverDeviceId) {
        this.receiverDeviceId = receiverDeviceId;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public void setSenderDeviceId(String senderDeviceId) {
        this.senderDeviceId = senderDeviceId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    @Override
    public String toString() {
        return "TransferRoute{" +
                "receiverDeviceId='" + receiverDeviceId + '\'' +
                ", transferId='" + transferId + '\'' +
                ", senderDeviceId='" + senderDeviceId + '\'' +
                '}';
    }
}
