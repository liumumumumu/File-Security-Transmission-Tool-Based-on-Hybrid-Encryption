package com.common.protocol.message;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 短信发送模式下的确认数据包
 *
 * */

public class TextMessageAckPacket extends Packet
{
    private final String messageId;//消息ID用来标识每一条消息
    private final boolean success;//消息是否发送成功
    private final String message;
    private final String senderAccountId;//发送方公钥指纹
    private final String receiverAccountId;//接收方公钥指纹
    private final String ackDeviceId;//确认接收的设备
    private final String ackAt;//确认于

    public TextMessageAckPacket(String messageId,
                                boolean success,
                                String message,
                                String senderAccountId,
                                String receiverAccountId,
                                String ackDeviceId,
                                String ackAt)
    {
        this.messageId = messageId;
        this.success = success;
        this.message = message;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.ackDeviceId = ackDeviceId;
        this.ackAt = ackAt;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Text_Message_Ack;
    }

    public String getMessageId() { return messageId; }
    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getSenderAccountId() { return senderAccountId; }
    public String getReceiverAccountId() { return receiverAccountId; }
    public String getAckDeviceId() { return ackDeviceId; }
    public String getAckAt() { return ackAt; }

    @Override
    public String toString()
    {
        return "TextMessageAckPacket{" +
                "messageId='" + messageId + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                ", senderAccountId='" + senderAccountId + '\'' +
                ", receiverAccountId='" + receiverAccountId + '\'' +
                '}';
    }
}
