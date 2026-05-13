package com.client;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

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
