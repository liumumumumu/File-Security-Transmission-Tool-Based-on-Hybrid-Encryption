package com.client.controller.dto;

public class SendFileRequest
{
    private String filePath;
    private String targetAccountId;
    private String targetDeviceId;

    public SendFileRequest(){}

    public SendFileRequest(String filePath, String targetAccountId, String targetDeviceId) {
        this.filePath = filePath;
        this.targetAccountId = targetAccountId;
        this.targetDeviceId = targetDeviceId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(String targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getTargetDeviceId() {
        return targetDeviceId;
    }

    public void setTargetDeviceId(String targetDeviceId) {
        this.targetDeviceId = targetDeviceId;
    }

    @Override
    public String toString() {
        return "SendFileRequest{" +
                "filePath='" + filePath + '\'' +
                ", targetAccountId='" + targetAccountId + '\'' +
                ", targetDeviceId='" + targetDeviceId + '\'' +
                '}';
    }
}
