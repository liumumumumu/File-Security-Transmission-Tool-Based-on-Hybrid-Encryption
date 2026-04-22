package com.session;

public enum TransferStatus
{
    PENDING,
    WAITING_FOR_TARGET,
    WAITING_FOR_ACCEPT,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    REJECTED
}
