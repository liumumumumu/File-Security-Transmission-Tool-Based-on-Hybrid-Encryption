package com.client.message;

import com.client.transport.TransportMode;

/**
 * Author: LQH
 * Date: 2026-05-27
 * Purpose: 消息会话摘要的数据对象
 * 用于展示会话的元数据，但是不展示正文的内容
 * */

public record ConversationSummary(
        String peerAccountId,//对方的账号ID
        String alias,//接收方的别名
        long unreadCount,//该会话里未读消息的数量
        String lastMessageTime,//最后一条消息的时间
        MessageDirection lastDirection,//最后一条消息的方向
        MessageStatus lastStatus,//最后一条消息的状态
        TransportMode lastMode//最后一条消息来自的模式
) {
}
