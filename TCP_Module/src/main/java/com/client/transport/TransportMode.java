package com.client.transport;

/**
 * Author: LQH
 * Date: 2026-05-21
 * Purpose: 传输模式标识枚举,用来区分当前文件传输走的是哪一个通道
 *
 * */

public enum TransportMode
{
    SERVER_RELAY,   //服务器中继
    DIRECT_PEER     //IPv6直连
}
