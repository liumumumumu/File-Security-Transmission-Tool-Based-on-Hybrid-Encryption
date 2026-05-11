package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class RetransmitRequestPacket extends Packet     //重传请求数据包
{
    private String transferId;
    private int startBlockId;
    private String reason;

    public RetransmitRequestPacket(String transferId, int startBlockId, String reason)
    {
        this.transferId = transferId;
        this.startBlockId = startBlockId;
        this.reason = reason;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Retransmit_Request;
    }

    @Override
    public String toString()
    {
        return "RetransmitRequestPacket{" +
                "transferId='" + transferId + '\'' +
                ", startBlockId=" + startBlockId +
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

    public int getStartBlockId()
    {
        return startBlockId;
    }

    public void setStartBlockId(int startBlockId)
    {
        this.startBlockId = startBlockId;
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
