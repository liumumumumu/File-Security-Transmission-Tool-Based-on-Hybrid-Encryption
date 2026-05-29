package com.client.message;

import com.client.ClientConnectionManager;
import com.client.direct.DirectPeerConnectionManager;
import com.client.service.LocalContactBookService;
import com.client.service.ClientTransferService;
import com.client.transport.PacketTransport;
import com.client.transport.TransportMode;
import com.common.crypto.AesGcmChunk;
import com.common.protocol.message.TextMessageAckPacket;
import com.common.protocol.message.TextMessagePacket;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import com.common.service.PushNotificationService;
import com.crypto.CryptoSupport;
import com.persistence.local.model.contactsRecord.ContactRecord;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 短信功能的核心业务服务。负责将发消息，收消息，已读回执连接起来。
 * 1.发送中继消息
 * 2.发送直连消息
 * 3.检验消息(非空，单条16Kb的长度限制，黑名单)
 * 4.生成messageId
 * 5.使用文件传输的同款加密方式,AES-GCM加密正文，RSA加密AES密钥
 * 6.等待ACK result，更新SENDING/ SENT/ FAILED
 * 7.接收消息后解密，写入内存历史，回复ACK
 * 8.处理重复的messageId
 * 9.处理已读回执，将自己发出的消息更新为READ
 * 10.用户查看完整对话后，将收到的未读消息标记为READ，并发送read receipt
 * 11.为直连模式查找active peer会话
 *
 * */

@Service
public class ClientMessageService
{
    public static final int MAX_MESSAGE_BYTES = 16 * 1024;

    private final ClientConnectionManager clientConnectionManager;//负责在Relay模式下的连接，认证和发送包
    private final CryptoSupport cryptoSupport;
    private final LocalContactBookService localContactBookService;
    private final MessageHistoryService messageHistoryService;
    private final PushNotificationService pushNotificationService;
    private final DirectPeerConnectionManager directPeerConnectionManager;//负责Direct模式下的会话管理
    private final ClientTransferService clientTransferService;
    private final com.common.config.ClientProperties clientProperties;
    private final com.common.config.NodeProperties nodeProperties;
    private final ConcurrentHashMap<String, CompletableFuture<TextMessageAckPacket>> ackFutures = new ConcurrentHashMap<>();

    public ClientMessageService(ClientConnectionManager clientConnectionManager,
                                CryptoSupport cryptoSupport,
                                LocalContactBookService localContactBookService,
                                MessageHistoryService messageHistoryService,
                                PushNotificationService pushNotificationService,
                                @Lazy DirectPeerConnectionManager directPeerConnectionManager,
                                @Lazy ClientTransferService clientTransferService,
                                com.common.config.ClientProperties clientProperties,
                                com.common.config.NodeProperties nodeProperties)
    {
        this.clientConnectionManager = clientConnectionManager;
        this.cryptoSupport = cryptoSupport;
        this.localContactBookService = localContactBookService;
        this.messageHistoryService = messageHistoryService;
        this.pushNotificationService = pushNotificationService;
        this.directPeerConnectionManager = directPeerConnectionManager;
        this.clientTransferService = clientTransferService;
        this.clientProperties = clientProperties;
        this.nodeProperties = nodeProperties;
    }

    //Relay模式下的发送消息处理函数
    public TextMessageRecord sendRelay(String targetToken, String text)
    {
        validateText(text);
        if(!clientConnectionManager.isAuthenticated())
        {
            throw new IllegalStateException("Client is not authenticated");
        }
        RelayTarget target = resolveRelayTarget(targetToken);
        if(localContactBookService.isBlacklisted(target.accountId()))
        {
            throw new IllegalStateException("Target account is blacklisted");
        }
        return sendWithAck(target.accountId(), target.publicKey(), text, TransportMode.SERVER_RELAY, clientConnectionManager::send);
    }

    //Direct模式下发送消息的处理函数
    public TextMessageRecord sendDirect(String text, DirectPeerConnectionManager.DirectPeerSession session)
    {
        validateText(text);
        if(session == null || !session.transport().isActive())
        {
            throw new IllegalStateException("Direct peer is not active");
        }
        if(localContactBookService.isBlacklisted(session.peerAccountId()))
        {
            throw new IllegalStateException("Target account is blacklisted");
        }
        return sendWithAck(session.peerAccountId(), session.peerPublicKey(), text, TransportMode.DIRECT_PEER, session.transport()::send);
    }

    public void handleAck(TextMessageAckPacket packet)
    {
        CompletableFuture<TextMessageAckPacket> future = ackFutures.remove(packet.getMessageId());
        if(future != null)
        {
            future.complete(packet);
            return;
        }
        if(packet.isSuccess())
        {
            messageHistoryService.markOutgoingSent(packet.getMessageId());
        }
        else
        {
            messageHistoryService.markOutgoingFailed(packet.getMessageId(), packet.getMessage());
        }
    }

    public void handleReadReceipt(TextMessageReadReceiptPacket packet)
    {
        messageHistoryService.markOutgoingRead(packet.getMessageId(), packet.getReadAt());
    }

    public void handleIncomingRelay(TextMessagePacket packet)
    {
        handleIncoming(packet, TransportMode.SERVER_RELAY, clientConnectionManager::send);
    }

    public void handleIncomingDirect(TextMessagePacket packet, PacketTransport transport)
    {
        handleIncoming(packet, TransportMode.DIRECT_PEER, transport::send);
    }

    public List<ConversationSummary> summaries()
    {
        return messageHistoryService.summaries();
    }

    public List<TextMessageRecord> conversation(String peerToken, boolean markRead)
    {
        String peerAccountId = resolveConversationPeer(peerToken);
        if(markRead)
        {
            markConversationRead(peerAccountId);
        }
        return messageHistoryService.conversation(peerAccountId);
    }

    public List<TextMessageRecord> directActiveConversation(boolean markRead)
    {
        DirectPeerConnectionManager.DirectPeerSession session = requireSingleActiveDirectSession();
        return conversation(session.peerAccountId(), markRead);
    }

    public DirectPeerConnectionManager.DirectPeerSession requireSingleActiveDirectSession()
    {
        int count = directPeerConnectionManager.activeSessionCount();
        if(count == 0)
        {
            throw new IllegalStateException("No active direct peer. Complete handshake first.");
        }
        if(count > 1)
        {
            throw new IllegalStateException("Multiple active direct peers. Use message <peerAccountId> to view; direct send selection is not available in v1.");
        }
        return directPeerConnectionManager.singleActiveSession().orElseThrow();
    }

    public String resolveConversationPeer(String peerToken)
    {
        if(peerToken == null || peerToken.isBlank())
        {
            throw new IllegalArgumentException("accountId is required");
        }
        if(peerToken.startsWith("contact-"))
        {
            int index = Integer.parseInt(peerToken.substring("contact-".length()));
            return localContactBookService.findContactByIndex(index)
                    .map(ContactRecord::getAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found: "+peerToken));
        }
        return peerToken;
    }

    private TextMessageRecord sendWithAck(String receiverAccountId,
                                          String receiverPublicKey,
                                          String text,
                                          TransportMode mode,
                                          PacketSender sender)
    {
        String messageId = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();
        String localAccountId = localAccountId();
        TextMessageRecord record = new TextMessageRecord(
                messageId,
                receiverAccountId,
                localAccountId,
                receiverAccountId,
                MessageDirection.OUTGOING,
                mode,
                text,
                createdAt,
                createdAt,
                MessageStatus.SENDING
        );
        messageHistoryService.add(record);
        CompletableFuture<TextMessageAckPacket> future = new CompletableFuture<>();
        ackFutures.put(messageId, future);
        try
        {
            sender.send(encryptedPacket(messageId, localAccountId, receiverAccountId, receiverPublicKey, text, createdAt));
            TextMessageAckPacket ack = future.get(clientProperties.getAckTimeoutSeconds(), TimeUnit.SECONDS);
            if(ack.isSuccess())
            {
                messageHistoryService.markOutgoingSent(messageId);
                return record;
            }
            messageHistoryService.markOutgoingFailed(messageId, ack.getMessage());
            throw new IllegalStateException(ack.getMessage());
        }
        catch(Exception ex)
        {
            ackFutures.remove(messageId);
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            messageHistoryService.markOutgoingFailed(messageId, message);
            throw new IllegalStateException("Message send failed: "+message, ex);
        }
    }

    private TextMessagePacket encryptedPacket(String messageId,
                                              String senderAccountId,
                                              String receiverAccountId,
                                              String receiverPublicKey,
                                              String text,
                                              String createdAt) throws GeneralSecurityException
    {
        SecretKey secretKey = cryptoSupport.generateAESKey();
        String encryptedSessionKey = cryptoSupport.encryptAESKeyForReceiver(secretKey, receiverPublicKey);
        AesGcmChunk chunk = cryptoSupport.encryptChunk(text.getBytes(StandardCharsets.UTF_8), secretKey);
        return new TextMessagePacket(
                messageId,
                senderAccountId,
                clientConnectionManager.getLocalPublicKey(),
                receiverAccountId,
                receiverPublicKey,
                createdAt,
                encryptedSessionKey,
                chunk.nonce(),
                chunk.ciphertext(),
                chunk.tag()
        );
    }

    private void handleIncoming(TextMessagePacket packet, TransportMode mode, PacketSender ackSender)
    {
        if(localContactBookService.isBlacklisted(packet.getSenderAccountId()))
        {
            ackSender.send(new TextMessageAckPacket(
                    packet.getMessageId(),
                    false,
                    "Sender is blacklisted",
                    packet.getSenderAccountId(),
                    packet.getReceiverAccountId(),
                    nodeProperties.getDeviceId(),
                    Instant.now().toString()
            ));
            return;
        }
        Optional<TextMessageRecord> duplicate = messageHistoryService.findByMessageId(packet.getMessageId());
        if(duplicate.isPresent())
        {
            TextMessageRecord existing = duplicate.get();
            ackSender.send(successAck(packet));
            if(existing.getStatus() == MessageStatus.READ)
            {
                sendReadReceipt(existing);
            }
            return;
        }

        try
        {
            SecretKey secretKey = cryptoSupport.decryptAESKey(packet.getEncryptedSessionKey());
            String body = new String(cryptoSupport.decryptChunk(packet.getNonce(), packet.getCiphertext(), packet.getTag(), secretKey), StandardCharsets.UTF_8);
            String receivedAt = Instant.now().toString();
            TextMessageRecord record = new TextMessageRecord(
                    packet.getMessageId(),
                    packet.getSenderAccountId(),
                    packet.getSenderAccountId(),
                    packet.getReceiverAccountId(),
                    MessageDirection.INCOMING,
                    mode,
                    body,
                    packet.getCreatedAt(),
                    receivedAt,
                    MessageStatus.UNREAD
            );
            messageHistoryService.add(record);
            ackSender.send(successAck(packet));
            pushNotificationService.publish("incoming-text-message", java.util.Map.of(
                    "senderAccountId", packet.getSenderAccountId(),
                    "messageId", packet.getMessageId()
            ));
        }
        catch(Exception ex)
        {
            ackSender.send(new TextMessageAckPacket(
                    packet.getMessageId(),
                    false,
                    ex.getMessage(),
                    packet.getSenderAccountId(),
                    packet.getReceiverAccountId(),
                    nodeProperties.getDeviceId(),
                    Instant.now().toString()
            ));
        }
    }

    private TextMessageAckPacket successAck(TextMessagePacket packet)
    {
        return new TextMessageAckPacket(
                packet.getMessageId(),
                true,
                "Message received",
                packet.getSenderAccountId(),
                packet.getReceiverAccountId(),
                nodeProperties.getDeviceId(),
                Instant.now().toString()
        );
    }

    private void markConversationRead(String peerAccountId)
    {
        String readAt = Instant.now().toString();
        for(TextMessageRecord record : messageHistoryService.markIncomingRead(peerAccountId, readAt))
        {
            sendReadReceipt(record);
        }
    }

    private void sendReadReceipt(TextMessageRecord record)
    {
        TextMessageReadReceiptPacket receipt = new TextMessageReadReceiptPacket(
                record.getMessageId(),
                record.getSenderAccountId(),
                record.getReceiverAccountId(),
                nodeProperties.getDeviceId(),
                record.getReadAt() == null ? Instant.now().toString() : record.getReadAt()
        );
        if(record.getMode() == TransportMode.SERVER_RELAY)
        {
            if(clientConnectionManager.isAuthenticated())
            {
                clientConnectionManager.send(receipt);
            }
            return;
        }
        directPeerConnectionManager.activeSessionForAccount(record.getSenderAccountId())
                .ifPresent(session -> session.transport().send(receipt));
    }

    private RelayTarget resolveRelayTarget(String targetToken)
    {
        if(targetToken == null || targetToken.isBlank())
        {
            throw new IllegalArgumentException("targetAccountId is required");
        }
        if(targetToken.startsWith("contact-"))
        {
            int index = Integer.parseInt(targetToken.substring("contact-".length()));
            ContactRecord contact = localContactBookService.findContactByIndex(index)
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found: "+targetToken));
            if(contact.getPublicKey() != null && !contact.getPublicKey().isBlank())
            {
                return new RelayTarget(contact.getAccountId(), contact.getPublicKey());
            }
            return searchRelayTarget(contact.getAccountId());
        }
        return searchRelayTarget(targetToken);
    }

    private RelayTarget searchRelayTarget(String accountId)
    {
        com.common.protocol.searchUser.OnlineUserSearchResultPacket result;
        try
        {
            result = clientTransferService.searchOnlineUser(accountId);
        }
        catch(Exception ex)
        {
            throw new IllegalStateException("Unable to resolve receiver publicKey: "+ex.getMessage(), ex);
        }
        if(!result.isSearchResult() || result.getPublicKey() == null || result.getPublicKey().isBlank())
        {
            throw new IllegalStateException("Unable to resolve receiver publicKey");
        }
        return new RelayTarget(result.getAccountId(), result.getPublicKey());
    }

    private String localAccountId()
    {
        try
        {
            return cryptoSupport.publicKeyFingerprint();
        }
        catch(Exception ex)
        {
            throw new IllegalStateException("Unable to calculate local accountId", ex);
        }
    }

    private void validateText(String text)
    {
        if(text == null || text.trim().isEmpty())
        {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if(text.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES)
        {
            throw new IllegalArgumentException("Message exceeds 16 KiB");
        }
    }

    private record RelayTarget(String accountId, String publicKey) {}

    @FunctionalInterface
    private interface PacketSender
    {
        void send(com.common.protocol.Packet packet);
    }
}
