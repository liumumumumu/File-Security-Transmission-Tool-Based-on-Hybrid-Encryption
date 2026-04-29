package com.common.codec.encoder.file;


import com.common.codec.encoder.Encoder;
import com.common.protocol.Packet;
import com.common.protocol.file.ReceiverDeviceSelectionPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class ReceiverDeviceSelectionEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if (!(packet instanceof ReceiverDeviceSelectionPacket selectionPacket)) {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type ReceiverDeviceSelectionPacket");
        }

        ByteBuf frame = Unpooled.buffer();
        frame.writeByte(selectionPacket.getMessageType());
        writeString(frame, selectionPacket.getTransferId());
        frame.writeInt(1);
        frame.writeByte(selectionPacket.isAccepted() ? 1 : 0);
        writeString(frame, selectionPacket.getMessage());
        return frame;
    }

    private void writeString(ByteBuf frame, String value)
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        frame.writeInt(bytes.length);
        frame.writeBytes(bytes);
    }
}
