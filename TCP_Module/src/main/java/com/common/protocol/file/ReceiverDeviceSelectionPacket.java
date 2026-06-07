package com.common.protocol.file;


import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class ReceiverDeviceSelectionPacket extends Packet
{
    private String transferId;
    private boolean accepted;
    private String message;

    public ReceiverDeviceSelectionPacket(String transferId, boolean accepted, String message)
    {
        this.transferId = transferId;
        this.accepted = accepted;
        this.message = message;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Receiver_Device_Selection;
    }

    public String getTransferId()
    {
        return transferId;
    }

    public boolean isAccepted()
    {
        return accepted;
    }

    public String getMessage()
    {
        return message;
    }

    @Override
    public String toString()
    {
        return "ReceiverDeviceSelectionPacket{" +
                "transferId='" + transferId + '\'' +
                ", accepted=" + accepted +
                ", message='" + message + '\'' +
                '}';
    }
}

