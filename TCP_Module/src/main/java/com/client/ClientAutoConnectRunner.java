package com.client;

import com.service.ClientTransferService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;


/**
 * Author: LQH
 * Date: 2026-04-23
 * Purpose: 在Spring Boot应用启动完成后，自动尝试连接服务器
 *
 * */


@Component      //被Spring 扫描并主持成Bean，Spring启动是会自动创建，并管理生命周期
public class ClientAutoConnectRunner
{
    private final ClientTransferService clientTransferService;

    public ClientAutoConnectRunner(ClientTransferService clientTransferService) {
        this.clientTransferService = clientTransferService;
    }

    //当Spring Boot 应用完全启动后，调用autoConnect函数
    @EventListener(ApplicationReadyEvent.class)//ApplicationReadyEvent表示应用已经准备好接收请求，Spring 容器初始化完成，Web服务启动完成
    public void autoConnect() throws Exception
    {
        clientTransferService.autoConnectIfConfigured();
    }
}
