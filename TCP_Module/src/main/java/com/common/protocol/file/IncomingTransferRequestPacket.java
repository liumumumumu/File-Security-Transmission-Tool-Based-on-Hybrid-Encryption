package com.common.protocol.file;


import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class IncomingTransferRequestPacket extends Packet
{
    private String transferId;
    private String senderDeviceId;
    private String targetAccountId;
    private String fileName;
    private long fileSize;
    private int totalBlocks;

    public IncomingTransferRequestPacket(
            String transferId,
            String senderDeviceId,
            String targetAccountId,
            String fileName,
            long fileSize,
            int totalBlocks
    )
    {
        this.transferId = transferId;
        this.senderDeviceId = senderDeviceId;
        this.targetAccountId = targetAccountId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.totalBlocks = totalBlocks;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Incoming_Transfer_Request;
    }

    public String getTransferId()
    {
        return transferId;
    }

    public String getSenderDeviceId()
    {
        return senderDeviceId;
    }

    public String getTargetAccountId()
    {
        return targetAccountId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public long getFileSize()
    {
        return fileSize;
    }

    public int getTotalBlocks()
    {
        return totalBlocks;
    }

    @Override
    public String toString()
    {
        return "IncomingTransferRequestPacket{" +
                "transferId='" + transferId + '\'' +
                ", senderDeviceId='" + senderDeviceId + '\'' +
                ", targetAccountId='" + targetAccountId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", fileSize=" + fileSize +
                ", totalBlocks=" + totalBlocks +
                '}';
    }
}
