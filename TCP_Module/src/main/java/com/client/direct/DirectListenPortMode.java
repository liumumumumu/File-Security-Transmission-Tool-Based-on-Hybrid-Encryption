package com.client.direct;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 直连模式下接收方监听端口的选择策略枚举类
 * 随机端口降低暴露面
 * 固定端口提高可部署性，不过需要更严格的网络控制
 *
 * */

public enum DirectListenPortMode
{
    RANDOM,     //使用随机的临时端口
    FIXED       //使用固定端口
}

/*
项目里接收端监听不是长期服务，
而是握手时临时打开。连接成功后会关闭 listenerChannel。
随机端口配合短生命周期，可以减少被扫描、被连接尝试的时间窗口。
 */