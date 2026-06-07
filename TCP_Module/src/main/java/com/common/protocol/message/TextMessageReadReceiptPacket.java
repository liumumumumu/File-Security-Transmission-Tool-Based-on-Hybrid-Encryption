package com.common.protocol.message;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 短信发送模式下的短信回执数据包
 *
 * */

public class TextMessageReadReceiptPacket extends Packet
{
    private final String messageId;
    private final String senderAccountId;
    private final String readerAccountId;
    private final String readerDeviceId;
    private final String readAt;

    public TextMessageReadReceiptPacket(String messageId,
                                        String senderAccountId,
                                        String readerAccountId,
                                        String readerDeviceId,
                                        String readAt)
    {
        this.messageId = messageId;
        this.senderAccountId = senderAccountId;
        this.readerAccountId = readerAccountId;
        this.readerDeviceId = readerDeviceId;
        this.readAt = readAt;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Text_Message_Read_Receipt;
    }

    public String getMessageId() { return messageId; }
    public String getSenderAccountId() { return senderAccountId; }
    public String getReaderAccountId() { return readerAccountId; }
    public String getReaderDeviceId() { return readerDeviceId; }
    public String getReadAt() { return readAt; }

    @Override
    public String toString()
    {
        return "TextMessageReadReceiptPacket{" +
                "messageId='" + messageId + '\'' +
                ", senderAccountId='" + senderAccountId + '\'' +
                ", readerAccountId='" + readerAccountId + '\'' +
                ", readAt='" + readAt + '\'' +
                '}';
    }
}
