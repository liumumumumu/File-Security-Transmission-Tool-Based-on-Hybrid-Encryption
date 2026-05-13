package com.server;

/**
 * Author: LQH
 * Date: 2026-04-29
 * Purpose: 服务端用于暂存“等待接收端设备确认/选择”的文件传输请求实体类。
 *
 * */

public class PendingTransferRequest
{
    private String transferId;
    private String senderDeviceId;
    private String targetAccountId;
    private String fileName;
    private long fileSize;
    private int totalBlocks;

    public PendingTransferRequest(String fileName, long fileSize, String senderDeviceId, String targetAccountId, int totalBlocks, String transferId) {
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.senderDeviceId = senderDeviceId;
        this.targetAccountId = targetAccountId;
        this.totalBlocks = totalBlocks;
        this.transferId = transferId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSenderDeviceId() {
        return senderDeviceId;
    }

    public void setSenderDeviceId(String senderDeviceId) {
        this.senderDeviceId = senderDeviceId;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(String targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    @Override
    public String toString() {
        return "PendingTransferRequest{" +
                "fileName='" + fileName + '\'' +
                ", transferId='" + transferId + '\'' +
                ", senderDeviceId='" + senderDeviceId + '\'' +
                ", targetAccountId='" + targetAccountId + '\'' +
                ", fileSize=" + fileSize +
                ", totalBlocks=" + totalBlocks +
                '}';
    }
}
