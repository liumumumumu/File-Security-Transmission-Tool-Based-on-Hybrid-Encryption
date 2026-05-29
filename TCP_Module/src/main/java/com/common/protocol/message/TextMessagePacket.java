package com.common.protocol.message;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 短信发送模式下的短信正文数据包
 *
 * */

public class TextMessagePacket extends Packet
{
    private final String messageId;
    private final String senderAccountId;
    private final String senderPublicKey;
    private final String receiverAccountId;
    private final String receiverPublicKey;
    private final String createdAt;
    private final String encryptedSessionKey;

    //短信正文的密文
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final byte[] tag;

    public TextMessagePacket(String messageId,
                             String senderAccountId,
                             String senderPublicKey,
                             String receiverAccountId,
                             String receiverPublicKey,
                             String createdAt,
                             String encryptedSessionKey,
                             byte[] nonce,
                             byte[] ciphertext,
                             byte[] tag)
    {
        this.messageId = messageId;
        this.senderAccountId = senderAccountId;
        this.senderPublicKey = senderPublicKey;
        this.receiverAccountId = receiverAccountId;
        this.receiverPublicKey = receiverPublicKey;
        this.createdAt = createdAt;
        this.encryptedSessionKey = encryptedSessionKey;
        this.nonce = nonce;
        this.ciphertext = ciphertext;
        this.tag = tag;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Text_Message;
    }

    public String getMessageId() { return messageId; }
    public String getSenderAccountId() { return senderAccountId; }
    public String getSenderPublicKey() { return senderPublicKey; }
    public String getReceiverAccountId() { return receiverAccountId; }
    public String getReceiverPublicKey() { return receiverPublicKey; }
    public String getCreatedAt() { return createdAt; }
    public String getEncryptedSessionKey() { return encryptedSessionKey; }
    public byte[] getNonce() { return nonce; }
    public byte[] getCiphertext() { return ciphertext; }
    public byte[] getTag() { return tag; }

    @Override
    public String toString()
    {
        return "TextMessagePacket{" +
                "messageId='" + messageId + '\'' +
                ", senderAccountId='" + senderAccountId + '\'' +
                ", receiverAccountId='" + receiverAccountId + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}
