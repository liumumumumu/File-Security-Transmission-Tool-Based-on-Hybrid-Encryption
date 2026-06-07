package com.server;

import org.springframework.stereotype.Component;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: 确定接收设备后的文件传输路由关系实体类
 *
 **/

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
