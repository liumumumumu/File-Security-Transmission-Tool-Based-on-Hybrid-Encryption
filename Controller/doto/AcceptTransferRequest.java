package com.controller.dto;

public class AcceptTransferRequest
{
    String transferId;

    public AcceptTransferRequest(String transferId) {
        this.transferId = transferId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }

    @Override
    public String toString() {
        return "AcceptTransferRequest{" +
                "transferId='" + transferId + '\'' +
                '}';
    }
}
