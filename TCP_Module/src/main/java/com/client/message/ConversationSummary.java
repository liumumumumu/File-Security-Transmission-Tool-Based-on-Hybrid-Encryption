package com.client.message;

import com.client.transport.TransportMode;

public record ConversationSummary(
        String peerAccountId,
        String alias,
        long unreadCount,
        String lastMessageTime,
        MessageDirection lastDirection,
        MessageStatus lastStatus,
        TransportMode lastMode
) {
}
