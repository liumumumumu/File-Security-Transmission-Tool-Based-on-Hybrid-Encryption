package com.client.message;

import com.client.transport.TransportMode;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class MessageHistoryServiceTest
{
    @Test
    public void summariesDoNotExposeBodyAndConversationMarksRead()
    {
        MessageHistoryService service = new MessageHistoryService(null);
        service.add(new TextMessageRecord(
                "message-1",
                "peer-1",
                "peer-1",
                "me",
                MessageDirection.INCOMING,
                TransportMode.SERVER_RELAY,
                "secret body",
                "2026-05-27T00:00:00Z",
                "2026-05-27T00:00:01Z",
                MessageStatus.UNREAD
        ));

        ConversationSummary summary = service.summaries().get(0);
        assertEquals("peer-1", summary.peerAccountId());
        assertEquals(1L, summary.unreadCount());

        List<TextMessageRecord> marked = service.markIncomingRead("peer-1", "2026-05-27T00:00:02Z");

        assertEquals(1, marked.size());
        assertEquals(MessageStatus.READ, service.conversation("peer-1").get(0).getStatus());
        assertEquals("secret body", service.conversation("peer-1").get(0).getBody());
    }

    @Test
    public void duplicateMessageIdDoesNotCreateSecondRecord()
    {
        MessageHistoryService service = new MessageHistoryService(null);
        service.add(record("same-id", "2026-05-27T00:00:00Z"));
        service.add(record("same-id", "2026-05-27T00:00:01Z"));

        assertEquals(1, service.allMessages().size());
    }

    @Test
    public void evictsOldestWhenMoreThanFiveThousand()
    {
        MessageHistoryService service = new MessageHistoryService(null);
        for(int i = 0; i < 5001; i++)
        {
            service.add(record("message-" + i, String.format("2026-05-27T00:00:%05dZ", i)));
        }

        assertEquals(5000, service.allMessages().size());
        assertFalse(service.findByMessageId("message-0").isPresent());
    }

    private TextMessageRecord record(String messageId, String time)
    {
        return new TextMessageRecord(
                messageId,
                "peer",
                "me",
                "peer",
                MessageDirection.OUTGOING,
                TransportMode.SERVER_RELAY,
                "body",
                time,
                time,
                MessageStatus.SENT
        );
    }
}
