package com.common.protocol.auth;

import com.common.protocol.MessageType;
import com.common.protocol.Packet;

public class AuthResultPacket extends Packet
{
    //登陆验证结果
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
