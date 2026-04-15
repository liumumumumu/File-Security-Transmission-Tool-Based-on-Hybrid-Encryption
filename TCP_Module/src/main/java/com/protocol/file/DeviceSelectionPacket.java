package com.protocol.file;

import com.protocol.MessageType;
import com.protocol.Packet;

public class DeviceSelectionPacket extends Packet
{
    private String transferId;
    private String selectedDeviceId;//使用UUID标识用户的设备，逻辑设备ID，而不是硬件ID
    private boolean confirmed;
    private String message;

    public DeviceSelectionPacket(boolean confirmed, String message, String selectedDeviceId, String transferId) {
        this.confirmed = confirmed;
        this.message = message;
        this.selectedDeviceId = selectedDeviceId;
        this.transferId = transferId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Device_Selection;
    }

    @Override
    public String toString() {
        return "DeviceSelectionPacket{" +
                "confirmed=" + confirmed +
                ", transferId='" + transferId + '\'' +
                ", selectedDeviceId='" + selectedDeviceId + '\'' +
                ", message='" + message + '\'' +
                '}';
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSelectedDeviceId() {
        return selectedDeviceId;
    }

    public void setSelectedDeviceId(String selectedDeviceId) {
        this.selectedDeviceId = selectedDeviceId;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
