package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class TransferCancelAckPacket extends Packet     //确认取消传输的数据包
{
    private String transferId;
    private String ackByDeviceId;
    private String status;
    private String message;

    public TransferCancelAckPacket(String transferId, String ackByDeviceId, String status, String message)
    {
        this.transferId = transferId;
        this.ackByDeviceId = ackByDeviceId;
        this.status = status;
        this.message = message;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Transfer_Cancel_Ack;
    }

    @Override
    public String toString()
    {
        return "TransferCancelAckPacket{" +
                "transferId='" + transferId + '\'' +
                ", ackByDeviceId='" + ackByDeviceId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public String getTransferId()
    {
        return transferId;
    }

    public void setTransferId(String transferId)
    {
        this.transferId = transferId;
    }

    public String getAckByDeviceId()
    {
        return ackByDeviceId;
    }

    public void setAckByDeviceId(String ackByDeviceId)
    {
        this.ackByDeviceId = ackByDeviceId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
