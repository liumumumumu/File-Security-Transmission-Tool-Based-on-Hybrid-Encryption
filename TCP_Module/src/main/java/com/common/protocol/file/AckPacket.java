package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
FileAcceptPacket与AckPacket的区别
FileAcceptPacket决定要不要开始传这一块
AckPacket决定这一块收到与否
*/

public class AckPacket extends Packet
{
    private String transferId;
    private int blockedId;
    private boolean success;

    public AckPacket(int blockedId, boolean success, String transferId) {
        this.blockedId = blockedId;
        this.success = success;
        this.transferId = transferId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.ACk;
    }

    @Override
    public String toString() {
        return "AckPacket{" +
                "blockedId=" + blockedId +
                ", transferId='" + transferId + '\'' +
                ", success=" + success +
                '}';
    }

    public int getBlockedId() {
        return blockedId;
    }

    public void setBlockedId(int blockedId) {
        this.blockedId = blockedId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
