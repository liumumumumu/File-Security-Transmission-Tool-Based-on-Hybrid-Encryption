package com.common.protocol.message;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class TextMessageAckPacket extends Packet
{
    private final String messageId;
    private final boolean success;
    private final String message;
    private final String senderAccountId;
    private final String receiverAccountId;
    private final String ackDeviceId;
    private final String ackAt;

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
