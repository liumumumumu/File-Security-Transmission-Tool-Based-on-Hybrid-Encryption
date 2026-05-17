package com.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/*
* Purpose: 服务端Spring应用的启动配置类。负责告诉Spring 服务端启动时要扫描哪些包，加载哪些类
*
*/

@SpringBootApplication(scanBasePackages = {
        "com.server",
        "com.common",
        "com.crypto"
})
@ConfigurationPropertiesScan(basePackages = "com.common.config")
public class ServerApplication
{
}
