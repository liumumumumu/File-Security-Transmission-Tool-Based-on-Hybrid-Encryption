package com.protocol;

public abstract class Packet //数据包
{
    public abstract byte getMessageType();
    public abstract String toString();
}

//类似统一的返回结果Result
