package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class RetransmitAckPacket extends Packet     //重传确认数据包
{
    private String transferId;
    private boolean accepted;
    private int startBlockId;
    private String message;

    public RetransmitAckPacket(String transferId, boolean accepted, int startBlockId, String message)
    {
        this.transferId = transferId;
        this.accepted = accepted;
        this.startBlockId = startBlockId;
        this.message = message;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Retransmit_Ack;
    }

    @Override
    public String toString()
    {
        return "RetransmitAckPacket{" +
                "transferId='" + transferId + '\'' +
                ", accepted=" + accepted +
                ", startBlockId=" + startBlockId +
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

    public boolean isAccepted()
    {
        return accepted;
    }

    public void setAccepted(boolean accepted)
    {
        this.accepted = accepted;
    }

    public int getStartBlockId()
    {
        return startBlockId;
    }

    public void setStartBlockId(int startBlockId)
    {
        this.startBlockId = startBlockId;
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
