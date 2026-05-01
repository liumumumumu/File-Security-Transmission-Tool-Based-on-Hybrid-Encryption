package com.session;

public enum TransferStatus  //传输状态枚举类
{
    PENDING,
    WAITING_FOR_TARGET,
    WAITING_FOR_ACCEPT,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    REJECTED
}
