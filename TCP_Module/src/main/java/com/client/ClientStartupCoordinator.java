package com.client;

import com.common.config.ClientProperties;
import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import com.crypto.CryptoSupport;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ClientStartupCoordinator
{
    private static final String BLOCK_REASON_KEY_MISSING = "KEY_MISSING";

    private final ClientProperties clientProperties;
    private final NodeProperties nodeProperties;
    private final CryptoSupport cryptoSupport;
    private final ClientConnectionManager clientConnectionManager;
    private final Path startupStatePath;
    private final Gson gson = new Gson();

    private boolean initialized;
    private boolean autoConnectBlocked;
    private String autoConnectBlockReason = "";
    private boolean lastKeyMissing = true;
    private Map<String, Object> lastKeyStatus = Map.of();

    public ClientStartupCoordinator(
            ClientProperties clientProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            ClientConnectionManager clientConnectionManager,
            LocalStorageProperties localStorageProperties
    )
    {
        this.clientProperties = clientProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.clientConnectionManager = clientConnectionManager;
        this.startupStatePath = Path.of(localStorageProperties.getStartupStatePath()).toAbsolutePath();
    }

    public synchronized Map<String, Object> handleApplicationReady()
    {
        Map<String, Object> status = refreshStartupStatus();
        if (shouldAutoConnectNow()) {
            continueAutoConnect();
            status = refreshStartupStatus();
        }
        return status;
    }

    public synchronized Map<String, Object> startupStatus()
    {
        return refreshStartupStatus();
    }

    public synchronized Map<String, Object> generateStartupKeyAndContinue()
    {
        Map<String, String> result = cryptoSupport.generateKeyPair();
        markKeySetupPrompted(true);
        continueAutoConnectIfBlockedByMissingKey();
        return Map.of(
                "success", true,
                "keyResult", result,
                "startupStatus", refreshStartupStatus()
        );
    }

    public synchronized Map<String, Object> skipStartupKeySetup()
    {
        markKeySetupPrompted(true);
        return refreshStartupStatus();
    }

    public synchronized void markKeyAvailableAndContinueAutoConnect()
    {
        markKeySetupPrompted(true);
        continueAutoConnectIfBlockedByMissingKey();
        refreshStartupStatus();
    }

    public boolean isKeyMissing(Map<String, ?> keyStatus)
    {
        return !isTruthy(keyStatus.get("hasPrivateKey"));
    }

    public synchronized boolean shouldPromptForStartupKeySetup()
    {
        Map<String, Object> status = refreshStartupStatus();
        return Boolean.TRUE.equals(status.get("shouldPromptKeySetup"));
    }

    private Map<String, Object> refreshStartupStatus()
    {
        StartupState state = readStartupState();
        try {
            lastKeyStatus = new LinkedHashMap<>(cryptoSupport.keyStatus());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to check key status", ex);
        }
        lastKeyMissing = isKeyMissing(lastKeyStatus);

        if (!lastKeyMissing && !state.keySetupPrompted) {
            markKeySetupPrompted(true);
            state.keySetupPrompted = true;
        }

        if (lastKeyMissing && nodeProperties.isAutoConnect()) {
            autoConnectBlocked = true;
            autoConnectBlockReason = BLOCK_REASON_KEY_MISSING;
        } else if (!lastKeyMissing && BLOCK_REASON_KEY_MISSING.equals(autoConnectBlockReason)) {
            autoConnectBlocked = false;
            autoConnectBlockReason = "";
        }

        initialized = true;
        return buildStartupStatus(state);
    }

    private boolean shouldAutoConnectNow()
    {
        return initialized
                && nodeProperties.isAutoConnect()
                && !lastKeyMissing
                && !clientConnectionManager.isAuthenticated();
    }

    private void continueAutoConnectIfBlockedByMissingKey()
    {
        boolean wasBlockedByMissingKey = autoConnectBlocked && BLOCK_REASON_KEY_MISSING.equals(autoConnectBlockReason);
        refreshStartupStatus();
        if (wasBlockedByMissingKey && !lastKeyMissing) {
            continueAutoConnect();
        }
    }

    private void continueAutoConnect()
    {
        if (!nodeProperties.isAutoConnect()) {
            return;
        }
        try {
            autoConnectBlocked = false;
            autoConnectBlockReason = "";
            clientConnectionManager.connectAndAuthenticate(clientProperties.getServerHost(), clientProperties.getServerPort())
                    .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Client auto-connect failed. Console remains available; use 'connect <host> <port>' to retry.", ex);
            System.out.println("Auto-connect failed: " + ex.getMessage());
            System.out.println("Console is still available. Try: connect <host> <port>");
        }
    }

    private Map<String, Object> buildStartupStatus(StartupState state)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyMissing", lastKeyMissing);
        payload.put("keySetupPrompted", state.keySetupPrompted);
        payload.put("shouldPromptKeySetup", lastKeyMissing && !state.keySetupPrompted);
        payload.put("autoConnectConfigured", nodeProperties.isAutoConnect());
        payload.put("autoConnectBlocked", autoConnectBlocked);
        payload.put("autoConnectBlockReason", autoConnectBlockReason);
        payload.put("recommendedAction", recommendedAction(state));
        payload.put("keyStatus", lastKeyStatus);
        payload.put("startupStatePath", startupStatePath.toString());
        return payload;
    }

    private String recommendedAction(StartupState state)
    {
        if (!lastKeyMissing) {
            return "NONE";
        }
        if (!state.keySetupPrompted) {
            return "GENERATE_OR_IMPORT_KEY";
        }
        return "MANUALLY_GENERATE_OR_IMPORT_KEY";
    }

    private StartupState readStartupState()
    {
        if (Files.notExists(startupStatePath)) {
            return new StartupState();
        }
        try {
            String json = Files.readString(startupStatePath);
            StartupState state = gson.fromJson(json, StartupState.class);
            return state == null ? new StartupState() : state;
        } catch (IOException | JsonSyntaxException ex) {
            log.warn("Failed to read startup state from {}", startupStatePath, ex);
            return new StartupState();
        }
    }

    private void markKeySetupPrompted(boolean value)
    {
        StartupState state = readStartupState();
        state.keySetupPrompted = value;
        writeStartupState(state);
    }

    private void writeStartupState(StartupState state)
    {
        try {
            Path parent = startupStatePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(startupStatePath, gson.toJson(state));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write startup state: " + startupStatePath, ex);
        }
    }

    private boolean isTruthy(Object value)
    {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static class StartupState
    {
        private boolean keySetupPrompted;
    }
}
