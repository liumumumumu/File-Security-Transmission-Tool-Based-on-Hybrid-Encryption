package com.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/*
* Purpose: 客户端Spring应用的启动配置类。负责告诉Spring 客户端启动时要扫描哪些包，加载哪些类
*
*/

@SpringBootApplication(scanBasePackages = {
        "com.client",
        "com.common",
        "com.crypto",
        "com.persistence.local",
        "com.session"
})
@ConfigurationPropertiesScan(basePackages = "com.common.config")
public class ClientApplication
{
}
