package com.client.message;

import com.client.service.LocalContactBookService;
import com.persistence.local.model.contactsRecord.ContactRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 将短信消息存储在内存里，如果用户退出了程序，短信记录随之消失
 * 1.把消息记录保存在内存里
 * 2.按messageId去重，避免重复消息写入两次
 * 3.查询某个账号的完整对话
 * 4.生成messsages命令需要的会话摘要
 * 5.标记某个会话的收到消息为READ
 * 6.更新自己发出的消息状态: SENT, FAILED, READ
 * 7.控制最多5000条消息，超过了就删除最久的消息
 *
 * */

@Service
public class MessageHistoryService
{
    private static final int MAX_MESSAGES = 5000;

    private final LocalContactBookService localContactBookService;
    private final Map<String, TextMessageRecord> messagesById = new LinkedHashMap<>();

    public MessageHistoryService(LocalContactBookService localContactBookService)
    {
        this.localContactBookService = localContactBookService;
    }

    public synchronized Optional<TextMessageRecord> findByMessageId(String messageId)
    {
        return Optional.ofNullable(messagesById.get(messageId));
    }

    public synchronized boolean contains(String messageId)
    {
        return messagesById.containsKey(messageId);
    }

    public synchronized void add(TextMessageRecord record)
    {
        messagesById.putIfAbsent(record.getMessageId(), record);
        evictOldestIfNecessary();
    }

    public synchronized List<TextMessageRecord> conversation(String peerAccountId)
    {
        return messagesById.values().stream()
                .filter(record -> record.getPeerAccountId().equals(peerAccountId))
                .sorted(Comparator.comparing(TextMessageRecord::getCreatedAt))
                .toList();
    }

    public synchronized List<TextMessageRecord> allMessages()
    {
        return new ArrayList<>(messagesById.values());
    }

    public synchronized List<ConversationSummary> summaries()
    {
        Map<String, List<TextMessageRecord>> byPeer = new LinkedHashMap<>();
        for(TextMessageRecord record : messagesById.values())
        {
            byPeer.computeIfAbsent(record.getPeerAccountId(), key -> new ArrayList<>()).add(record);
        }

        List<ConversationSummary> summaries = new ArrayList<>();
        for(Map.Entry<String, List<TextMessageRecord>> entry : byPeer.entrySet())
        {
            List<TextMessageRecord> records = entry.getValue();
            TextMessageRecord last = records.stream()
                    .max(Comparator.comparing(this::localRecordTime))
                    .orElseThrow();
            long unreadCount = records.stream()
                    .filter(record -> record.getDirection() == MessageDirection.INCOMING)
                    .filter(record -> record.getStatus() == MessageStatus.UNREAD)
                    .count();
            summaries.add(new ConversationSummary(
                    entry.getKey(),
                    aliasFor(entry.getKey()),
                    unreadCount,
                    localRecordTime(last),
                    last.getDirection(),
                    last.getStatus(),
                    last.getMode()
            ));
        }
        summaries.sort(Comparator.comparing(ConversationSummary::lastMessageTime).reversed());
        return summaries;
    }

    public synchronized List<TextMessageRecord> markIncomingRead(String peerAccountId, String readAt)
    {
        List<TextMessageRecord> marked = new ArrayList<>();
        for(TextMessageRecord record : messagesById.values())
        {
            if(record.getPeerAccountId().equals(peerAccountId)
                    && record.getDirection() == MessageDirection.INCOMING
                    && record.getStatus() == MessageStatus.UNREAD)
            {
                record.markRead(readAt);
                marked.add(record);
            }
        }
        return marked;
    }

    public synchronized void markOutgoingSent(String messageId)
    {
        findByMessageId(messageId).ifPresent(record -> record.updateStatus(MessageStatus.SENT));
    }

    public synchronized void markOutgoingRead(String messageId, String readAt)
    {
        findByMessageId(messageId)
                .filter(record -> record.getDirection() == MessageDirection.OUTGOING)
                .ifPresent(record -> record.markRead(readAt));
    }

    public synchronized void markOutgoingFailed(String messageId, String errorMessage)
    {
        findByMessageId(messageId).ifPresent(record -> record.fail(errorMessage));
    }

    private String localRecordTime(TextMessageRecord record)
    {
        return record.getReceivedAt() == null || record.getReceivedAt().isBlank()
                ? record.getCreatedAt()
                : record.getReceivedAt();
    }

    private void evictOldestIfNecessary()
    {
        while(messagesById.size() > MAX_MESSAGES)
        {
            String oldestId = messagesById.values().stream()
                    .min(Comparator.comparing(this::localRecordTime))
                    .map(TextMessageRecord::getMessageId)
                    .orElse(null);
            if(oldestId == null)
            {
                return;
            }
            messagesById.remove(oldestId);
        }
    }

    private String aliasFor(String accountId)
    {
        if(localContactBookService == null)
        {
            return null;
        }
        return localContactBookService.findContactByAccountId(accountId)
                .map(ContactRecord::getAlias)
                .orElse(null);
    }
}
