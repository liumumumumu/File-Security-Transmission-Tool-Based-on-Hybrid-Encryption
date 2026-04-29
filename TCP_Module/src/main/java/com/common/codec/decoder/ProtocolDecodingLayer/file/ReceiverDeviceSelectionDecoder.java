package com.common.codec.decoder.ProtocolDecodingLayer.file;


import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.protocol.MessageType;
import com.common.protocol.file.ReceiverDeviceSelectionPacket;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

public class ReceiverDeviceSelectionDecoder extends Decoder
{
    @Override
    public ReceiverDeviceSelectionPacket decode(ByteBuf in)
    {
        byte messageType = in.readByte();
        if (messageType != MessageType.Receiver_Device_Selection) {
            throw new IllegalStateException("Decoder misuse; " + messageType + " packet shouldn't be decoded by ReceiverDeviceSelectionDecoder");
        }
        String transferId = readString(in);
        int acceptedLength = in.readInt();
        if (acceptedLength != 1) {
            throw new IllegalStateException("accepted field length must be 1, but got " + acceptedLength);
        }
        byte acceptedByte = in.readByte();
        if (acceptedByte != 0 && acceptedByte != 1) {
            throw new IllegalStateException("accepted field value must be 0 or 1, but got " + acceptedByte);
        }
        return new ReceiverDeviceSelectionPacket(transferId, acceptedByte == 1, readString(in));
    }

    private String readString(ByteBuf in)
    {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}