package com.protocol.auth;

import com.protocol.MessageType;
import com.protocol.Packet;

public class AuthResultPacket extends Packet
{
    private boolean success;
    private String message;

    public AuthResultPacket(String message, boolean success) {
        this.message = message;
        this.success = success;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.Auth_Result;
    }

    @Override
    public String toString() {
        return "AuthResultPacket{" +
                "message='" + message + '\'' +
                ", success=" + success +
                '}';
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
