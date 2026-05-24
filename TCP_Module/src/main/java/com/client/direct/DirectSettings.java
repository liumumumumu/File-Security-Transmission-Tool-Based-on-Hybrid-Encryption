package com.client.direct;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 直连传输模块的配置数据类，用来保存接收方监听策略
 *
 * */

public class DirectSettings
{
    private DirectListenPortMode listenPortMode = DirectListenPortMode.RANDOM;//监听端口模式，默认是RANDOM(允许用户自己设置)
    private int fixedListenPort;//表示端口的默认值

    public DirectListenPortMode getListenPortMode() {
        return listenPortMode == null ? DirectListenPortMode.RANDOM : listenPortMode;
    }

    public void setListenPortMode(DirectListenPortMode listenPortMode) {
        this.listenPortMode = listenPortMode;
    }

    public int getFixedListenPort() {
        return fixedListenPort;
    }

    public void setFixedListenPort(int fixedListenPort) {
        this.fixedListenPort = fixedListenPort;
    }
}


