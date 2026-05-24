package com.client;

import com.client.language.ConsoleMessages;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ClientAutoConnectRunner
{
    private final ClientStartupCoordinator clientStartupCoordinator;
    private final ConsoleMessages messages;

    public ClientAutoConnectRunner(ClientStartupCoordinator clientStartupCoordinator, ConsoleMessages messages) {
        this.clientStartupCoordinator = clientStartupCoordinator;
        this.messages = messages;
    }

    //当Spring Boot 应用完全启动后，调用autoConnect函数
    @EventListener(ApplicationReadyEvent.class)//ApplicationReadyEvent表示应用已经准备好接收请求，Spring 容器初始化完成，Web服务启动完成
    public void autoConnect()
    {
        try {
            clientStartupCoordinator.handleApplicationReady();
        } catch (Exception ex) {
            log.warn("Client startup coordination failed.", ex);
            System.out.println(messages.format(ConsoleMessages.Key.STARTUP_CHECK_FAILED, ex.getMessage()));
            System.out.println(messages.text(ConsoleMessages.Key.CONSOLE_AVAILABLE_TRY_KEY_INFO));
        }
    }
}
