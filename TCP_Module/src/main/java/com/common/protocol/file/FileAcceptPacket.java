package com.common.protocol.file;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/*
FileAcceptPacket与AckPacket的区别
FileAcceptPacket决定要不要开始传这一块
AckPacket决定这一块收到与否
*/

public class FileAcceptPacket extends Packet
{
    //文件接收数据包
    private String transferId;
    private boolean accept;
    private String message;

    public FileAcceptPacket(boolean accept, String message, String transferId) {
        this.accept = accept;
        this.message = message;
        this.transferId = transferId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.File_Accept;
    }

    @Override
    public String toString() {
        return "FileAcceptPacket{" +
                "accept=" + accept +
                ", transferId='" + transferId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public boolean isAccept() {
        return accept;
    }

    public void setAccept(boolean accept) {
        this.accept = accept;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
