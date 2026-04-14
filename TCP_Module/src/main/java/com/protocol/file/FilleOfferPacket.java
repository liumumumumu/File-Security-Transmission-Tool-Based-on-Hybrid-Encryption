package com.protocol.file;

import com.protocol.MessageType;
import com.protocol.Packet;

public class FilleOfferPacket extends Packet
{
    private String transferId;//每次传输对应一个transferId。使用uuid生成
    private String senderPublicKey;
    private String receiverPublicKey;
    private String encryptedSessionKey;
    private String fileName;
    private long fileSize;
    private int totalBlocks;

    public FilleOfferPacket(String encryptedSessionKey, String fileName, long fileSize, String receiverPublicKey, String senderPublicKey, int totalBlocks, String transferId) {
        this.encryptedSessionKey = encryptedSessionKey;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.receiverPublicKey = receiverPublicKey;
        this.senderPublicKey = senderPublicKey;
        this.totalBlocks = totalBlocks;
        this.transferId = transferId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.File_Offer;
    }

    @Override
    public String toString() {
        return "FilleOfferPacket{" +
                "encryptedSessionKey='" + encryptedSessionKey + '\'' +
                ", transferId='" + transferId + '\'' +
                ", senderPublicKey='" + senderPublicKey + '\'' +
                ", receiverPublicKey='" + receiverPublicKey + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", totalBlocks=" + totalBlocks +
                '}';
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

    public String getReceiverPublicKey() {
        return receiverPublicKey;
    }

    public void setReceiverPublicKey(String receiverPublicKey) {
        this.receiverPublicKey = receiverPublicKey;
    }

    public String getSenderPublicKey() {
        return senderPublicKey;
    }

    public void setSenderPublicKey(String senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
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

    public String getEncryptedSessionKey() {
        return encryptedSessionKey;
    }

    public void setEncryptedSessionKey(String encryptedSessionKey) {
        this.encryptedSessionKey = encryptedSessionKey;
    }
}
