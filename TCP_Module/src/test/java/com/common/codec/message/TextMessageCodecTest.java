package com.common.codec.message;

import com.common.codec.decoder.ProtocolDecodingLayer.message.TextMessageAckDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.message.TextMessageDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.message.TextMessageReadReceiptDecoder;
import com.common.codec.encoder.message.TextMessageAckEncoder;
import com.common.codec.encoder.message.TextMessageEncoder;
import com.common.codec.encoder.message.TextMessageReadReceiptEncoder;
import com.common.protocol.message.TextMessageAckPacket;
import com.common.protocol.message.TextMessagePacket;
import com.common.protocol.message.TextMessageReadReceiptPacket;
import io.netty.buffer.ByteBuf;
import org.junit.Test;

import static org.junit.Assert.*;

public class TextMessageCodecTest
{
    @Test
    public void encodesAndDecodesTextMessage()
    {
        TextMessagePacket packet = new TextMessagePacket(
                "message-1",
                "sender-account",
                "sender-public",
                "receiver-account",
                "receiver-public",
                "2026-05-27T00:00:00Z",
                "encrypted-key",
                new byte[]{1, 2},
                new byte[]{3, 4, 5},
                new byte[]{6}
        );

        ByteBuf encoded = new TextMessageEncoder().encode(packet);
        TextMessagePacket decoded = new TextMessageDecoder().decode(encoded);

        assertEquals(packet.getMessageId(), decoded.getMessageId());
        assertEquals(packet.getSenderAccountId(), decoded.getSenderAccountId());
        assertEquals(packet.getReceiverAccountId(), decoded.getReceiverAccountId());
        assertArrayEquals(packet.getNonce(), decoded.getNonce());
        assertArrayEquals(packet.getCiphertext(), decoded.getCiphertext());
        assertArrayEquals(packet.getTag(), decoded.getTag());
    }

    @Test
    public void encodesAndDecodesAck()
    {
        TextMessageAckPacket packet = new TextMessageAckPacket(
                "message-1",
                true,
                "ok",
                "sender",
                "receiver",
                "device",
                "2026-05-27T00:00:01Z"
        );

        ByteBuf encoded = new TextMessageAckEncoder().encode(packet);
        TextMessageAckPacket decoded = new TextMessageAckDecoder().decode(encoded);

        assertEquals(packet.getMessageId(), decoded.getMessageId());
        assertTrue(decoded.isSuccess());
        assertEquals(packet.getAckDeviceId(), decoded.getAckDeviceId());
    }

    @Test
    public void encodesAndDecodesReadReceipt()
    {
        TextMessageReadReceiptPacket packet = new TextMessageReadReceiptPacket(
                "message-1",
                "sender",
                "reader",
                "reader-device",
                "2026-05-27T00:00:02Z"
        );

        ByteBuf encoded = new TextMessageReadReceiptEncoder().encode(packet);
        TextMessageReadReceiptPacket decoded = new TextMessageReadReceiptDecoder().decode(encoded);

        assertEquals(packet.getMessageId(), decoded.getMessageId());
        assertEquals(packet.getSenderAccountId(), decoded.getSenderAccountId());
        assertEquals(packet.getReaderAccountId(), decoded.getReaderAccountId());
        assertEquals(packet.getReadAt(), decoded.getReadAt());
    }
}
