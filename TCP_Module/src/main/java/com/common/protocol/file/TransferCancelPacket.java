package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class TransferCancelPacket extends Packet    //取消传输数据包
{
    private String transferId;
    private String reason;

    public TransferCancelPacket(String transferId, String reason)
    {
        this.transferId = transferId;
        this.reason = reason;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Transfer_Cancel;
    }

    @Override
    public String toString()
    {
        return "TransferCancelPacket{" +
                "transferId='" + transferId + '\'' +
                ", reason='" + reason + '\'' +
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

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }
}
