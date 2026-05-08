package com.client;

import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

@Component
@Profile("client")
public class ClientDeviceIdInitializer
{
    private static final Logger log = LoggerFactory.getLogger(ClientDeviceIdInitializer.class);
    private static final Set<String> PLACEHOLDER_DEVICE_IDS = Set.of("client-device-1", "device-default");

    private final NodeProperties nodeProperties;
    private final LocalStorageProperties localStorageProperties;

    public ClientDeviceIdInitializer(NodeProperties nodeProperties, LocalStorageProperties localStorageProperties)
    {
        this.nodeProperties = nodeProperties;
        this.localStorageProperties = localStorageProperties;
    }

    @PostConstruct
    public void initializeDeviceId() throws Exception
    {
        String configuredDeviceId = nodeProperties.getDeviceId();
        if (hasExplicitDeviceId(configuredDeviceId)) {
            log.info("Using configured client deviceId={}", configuredDeviceId);
            return;
        }

        Path deviceIdPath = Path.of(localStorageProperties.getDeviceIdPath()).toAbsolutePath();
        Path parent = deviceIdPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String deviceId = readExistingDeviceId(deviceIdPath);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            Files.writeString(deviceIdPath, deviceId);
            log.info("Generated new client deviceId={}, path={}", deviceId, deviceIdPath);
        } else {
            log.info("Loaded client deviceId={}, path={}", deviceId, deviceIdPath);
        }

        nodeProperties.setDeviceId(deviceId);
    }

    private boolean hasExplicitDeviceId(String deviceId)
    {
        return deviceId != null && !deviceId.isBlank() && !PLACEHOLDER_DEVICE_IDS.contains(deviceId);
    }

    private String readExistingDeviceId(Path deviceIdPath) throws Exception
    {
        if (!Files.exists(deviceIdPath)) {
            return null;
        }

        String value = Files.readString(deviceIdPath).trim();
        if (value.isBlank()) {
            return null;
        }
        return value;
    }
}
