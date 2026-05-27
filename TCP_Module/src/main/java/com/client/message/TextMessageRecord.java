package com.client.message;

import com.client.transport.TransportMode;

public class TextMessageRecord
{
    private final String messageId;
    private final String peerAccountId;
    private final String senderAccountId;
    private final String receiverAccountId;
    private final MessageDirection direction;
    private final TransportMode mode;
    private final String body;
    private final String createdAt;
    private final String receivedAt;
    private String readAt;
    private MessageStatus status;
    private String errorMessage;

    public TextMessageRecord(String messageId,
                             String peerAccountId,
                             String senderAccountId,
                             String receiverAccountId,
                             MessageDirection direction,
                             TransportMode mode,
                             String body,
                             String createdAt,
                             String receivedAt,
                             MessageStatus status)
    {
        this.messageId = messageId;
        this.peerAccountId = peerAccountId;
        this.senderAccountId = senderAccountId;
        this.receiverAccountId = receiverAccountId;
        this.direction = direction;
        this.mode = mode;
        this.body = body;
        this.createdAt = createdAt;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    public String getMessageId() { return messageId; }
    public String getPeerAccountId() { return peerAccountId; }
    public String getSenderAccountId() { return senderAccountId; }
    public String getReceiverAccountId() { return receiverAccountId; }
    public MessageDirection getDirection() { return direction; }
    public TransportMode getMode() { return mode; }
    public String getBody() { return body; }
    public String getCreatedAt() { return createdAt; }
    public String getReceivedAt() { return receivedAt; }
    public String getReadAt() { return readAt; }
    public MessageStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }

    public void updateStatus(MessageStatus status)
    {
        this.status = status;
    }

    public void markRead(String readAt)
    {
        this.status = MessageStatus.READ;
        this.readAt = readAt;
    }

    public void fail(String errorMessage)
    {
        this.status = MessageStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
