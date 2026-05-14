package com.session;

public enum TransferStatus  //传输状态枚举类
{
    PENDING,
    WAITING_FOR_TARGET,
    WAITING_FOR_ACCEPT,
    TRANSFERRING,
    COMPLETED,
    CANCELED,
    FAILED,
    REJECTED;

    public boolean isTerminal()
    {
        return this == COMPLETED || this == CANCELED || this == FAILED || this == REJECTED;
    }
}
