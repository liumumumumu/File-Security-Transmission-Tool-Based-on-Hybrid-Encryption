package com;

import com.client.ClientApplication;
import com.server.ServerApplication;
import org.springframework.boot.SpringApplication;

public class FileSecurityTransmissionApplication
{
    public static void main(String[] args)
    {
        String role = resolveRole(args);
        if ("server".equalsIgnoreCase(role)) {
            SpringApplication application = new SpringApplication(ServerApplication.class);
            application.setAdditionalProfiles("server");
            application.run(args);
            return;
        }
        SpringApplication application = new SpringApplication(ClientApplication.class);
        application.setAdditionalProfiles("client");
        application.run(args);
    }

    private static String resolveRole(String[] args)
    {
        String envRole = System.getenv("APP_ROLE");
        if (envRole != null && !envRole.isBlank()) {
            return envRole;
        }

        for (String arg : args) {
            if (arg.startsWith("--app.role=")) {
                return arg.substring("--app.role=".length());
            }
        }
        return "client";
    }
}
