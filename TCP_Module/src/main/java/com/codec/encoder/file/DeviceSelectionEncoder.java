package com.codec.encoder.file;

import com.codec.encoder.Encoder;
import com.protocol.Packet;
import com.protocol.file.DeviceSelectionPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;

public class DeviceSelectionEncoder extends Encoder
{
    @Override
    public ByteBuf encode(Packet packet)
    {
        if(!(packet instanceof DeviceSelectionPacket))
        {
            throw new IllegalArgumentException("Packet type mismatch; packet must be of type DeviceSelectionPacket");
        }

        DeviceSelectionPacket DSPacket = (DeviceSelectionPacket) packet;

        ByteBuf startFrame = Unpooled.buffer();
        startFrame.writeByte(DSPacket.getMessageType());

        byte[] transferIdBytes=DSPacket.getTransferId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(transferIdBytes.length);
        startFrame.writeBytes(transferIdBytes);

        byte[] selectedDeviceIdBytes=DSPacket.getSelectedDeviceId().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(selectedDeviceIdBytes.length);
        startFrame.writeBytes(selectedDeviceIdBytes);

        startFrame.writeInt(1);
        startFrame.writeByte(DSPacket.isConfirmed()? 1: 0);

        byte[] messageBytes=DSPacket.getMessage().getBytes(StandardCharsets.UTF_8);
        startFrame.writeInt(messageBytes.length);
        startFrame.writeBytes(messageBytes);

        return startFrame;
    }
}
