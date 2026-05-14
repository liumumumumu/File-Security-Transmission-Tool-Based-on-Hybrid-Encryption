package com.server;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.server",
        "com.common",
        "com.crypto"
})
@ConfigurationPropertiesScan(basePackages = "com.common.config")
public class ServerApplication
{
}
