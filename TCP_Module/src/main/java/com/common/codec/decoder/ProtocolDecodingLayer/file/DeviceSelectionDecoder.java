package com.common.codec.decoder.ProtocolDecodingLayer.file;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.DeviceSelectionPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class DeviceSelectionDecoder extends Decoder
{
    @Override
    public DeviceSelectionPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if(messageType != MessageType.Device_Selection)
        {
            throw new IllegalStateException("Decoder misuse; "+messageType+" packet shouldn't be decoded by DeviceSelectionDecoder");
        }

        int transferIdLength = in.readInt();
        byte[] transferIdBytes = new byte[transferIdLength];
        in.readBytes(transferIdBytes);
        String transferId = new String(transferIdBytes, StandardCharsets.UTF_8);

        int selectedDeviceIdLength = in.readInt();
        byte[] selectedDeviceIdBytes = new byte[selectedDeviceIdLength];
        in.readBytes(selectedDeviceIdBytes);
        String selectedDeviceId = new String(selectedDeviceIdBytes, StandardCharsets.UTF_8);

        int confirmedLength = in.readInt();
        if (confirmedLength != 1)
        {
            throw new IllegalStateException(
                    "success field length must be 1, but got " + confirmedLength
            );
        }

        byte confirmedByte = in.readByte();
        if (confirmedByte != 0 && confirmedByte != 1)
        {
            throw new IllegalStateException(
                    "success field value must be 0 or 1, but got " + confirmedByte
            );
        }
        boolean confirmed = (confirmedByte == 1);

        int messageLength = in.readInt();
        byte[] messageBytes = new byte[messageLength];
        in.readBytes(messageBytes);
        String message = new String(messageBytes, StandardCharsets.UTF_8);

        return new DeviceSelectionPacket(confirmed, message, selectedDeviceId, transferId);
    }
}
