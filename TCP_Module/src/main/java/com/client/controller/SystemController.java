package com.client.controller;

import com.client.ApplicationShutdownService;
import com.client.ClientConnectionManager;
import com.client.ClientStartupCoordinator;
import com.client.controller.dto.ConnectRequest;
import com.client.language.LanguageSettingsService;
import com.client.language.UiLanguage;
import com.common.config.ClientProperties;
import com.common.config.CryptoServiceProperties;
import com.common.config.NodeProperties;
import com.common.config.ServerProperties;
import com.client.controller.dto.ImportPrivateKeyRequest;
import com.client.controller.dto.PublicKeyFingerprintRequest;
import com.common.util.PathInputNormalizer;
import com.crypto.CryptoSupport;
import com.client.service.LocalTransferHistoryService;
import com.client.service.PrivateKeyArtifactService;
import com.client.service.TransferTaskRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/system")
public class SystemController
{
    private final ClientProperties clientProperties;
    private final ServerProperties serverProperties;
    private final CryptoServiceProperties cryptoServiceProperties;
    private final NodeProperties nodeProperties;
    private final CryptoSupport cryptoSupport;
    private final ClientStartupCoordinator clientStartupCoordinator;
    private final TransferTaskRegistry transferTaskRegistry;
    private final LocalTransferHistoryService localTransferHistoryService;
    private final ClientConnectionManager clientConnectionManager;
    private final ApplicationShutdownService applicationShutdownService;
    private final PrivateKeyArtifactService privateKeyArtifactService;
    private final LanguageSettingsService languageSettingsService;

    public SystemController(
            ClientProperties clientProperties,
            ServerProperties serverProperties,
            CryptoServiceProperties cryptoServiceProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            ClientStartupCoordinator clientStartupCoordinator,
            TransferTaskRegistry transferTaskRegistry,
            LocalTransferHistoryService localTransferHistoryService,
            ClientConnectionManager clientConnectionManager,
            ApplicationShutdownService applicationShutdownService,
            PrivateKeyArtifactService privateKeyArtifactService,
            LanguageSettingsService languageSettingsService
    )
    {
        this.clientProperties = clientProperties;
        this.serverProperties = serverProperties;
        this.cryptoServiceProperties = cryptoServiceProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.clientStartupCoordinator = clientStartupCoordinator;
        this.transferTaskRegistry = transferTaskRegistry;
        this.localTransferHistoryService = localTransferHistoryService;
        this.clientConnectionManager = clientConnectionManager;
        this.applicationShutdownService = applicationShutdownService;
        this.privateKeyArtifactService = privateKeyArtifactService;
        this.languageSettingsService = languageSettingsService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() throws Exception
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", "file-security-transmission-tool");
        payload.put("status", "UP");
        payload.put("deviceId", nodeProperties.getDeviceId());
        Map<String, Object> keyStatus = cryptoSupport.keyStatus();
        payload.put("accountId", clientStartupCoordinator.isKeyMissing(keyStatus) ? "" : cryptoSupport.publicKeyFingerprint());
        payload.put("clientServerHost", clientProperties.getServerHost());
        payload.put("clientServerPort", clientProperties.getServerPort());
        payload.put("tcpEnabled", serverProperties.isEnabled());
        payload.put("tcpBindHost", serverProperties.getBindHost());
        payload.put("tcpBindPort", serverProperties.getBindPort());
        payload.put("cryptoServiceAddress", cryptoServiceProperties.getAddress());
        payload.put("cryptoServicePort", cryptoServiceProperties.getPort());
        payload.put("taskCount", transferTaskRegistry.allTasks().size());
        payload.put("localTransferHistoryPath", localTransferHistoryService.historyPath().toString());
        return payload;
    }

    @GetMapping("/key")
    public ResponseEntity<Map<String, Object>> key() throws Exception
    {
        return ResponseEntity.ok(cryptoSupport.keyStatus());
    }

    @PostMapping("/key/generate")
    public ResponseEntity<Map<String, String>> generateKey() throws Exception
    {
        Map<String, String> result = cryptoSupport.generateKeyPair();
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/key/delete")
    public ResponseEntity<Map<String, String>> deleteKey() throws Exception
    {
        return ResponseEntity.ok(cryptoSupport.deleteKeyPair());
    }

    @PostMapping("/key/import-private")
    public ResponseEntity<Map<String, Object>> importPrivateKey(@RequestBody ImportPrivateKeyRequest request) throws Exception
    {
        if (request == null) {
            throw new IllegalArgumentException("privateKey or privateKeyPath is required");
        }
        if (request.getPrivateKey() != null && !request.getPrivateKey().isBlank()) {
            privateKeyArtifactService.importPrivateKey(request.getPrivateKey());
        } else if (request.getPrivateKeyPath() != null && !request.getPrivateKeyPath().isBlank()) {
            privateKeyArtifactService.importPrivateKey(PathInputNormalizer.toPath(request.getPrivateKeyPath()));
        } else {
            throw new IllegalArgumentException("privateKey or privateKeyPath is required");
        }
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        return ResponseEntity.ok(cryptoSupport.keyStatus());
    }

    @PostMapping("/key/fingerprint")
    public ResponseEntity<Map<String, String>> fingerprint(@RequestBody(required = false) PublicKeyFingerprintRequest request) throws Exception
    {
        String fingerprint = request == null || request.getPublicKey() == null || request.getPublicKey().isBlank()
                ? cryptoSupport.publicKeyFingerprint()
                : cryptoSupport.publicKeyFingerprint(request.getPublicKey());
        return ResponseEntity.ok(Map.of(
                "fingerprint", fingerprint
        ));
    }

    @GetMapping({"/key/fingerprint", "/account-id"})
    public ResponseEntity<Map<String, String>> localFingerprint() throws Exception
    {
        String accountId = cryptoSupport.publicKeyFingerprint();
        return ResponseEntity.ok(Map.of(
                "fingerprint", accountId,
                "accountId", accountId
        ));
    }

    @PostMapping("/account-id")
    public ResponseEntity<Map<String, String>> accountId(@RequestBody(required = false) PublicKeyFingerprintRequest request) throws Exception
    {
        String accountId = request == null || request.getPublicKey() == null || request.getPublicKey().isBlank()
                ? cryptoSupport.publicKeyFingerprint()
                : cryptoSupport.publicKeyFingerprint(request.getPublicKey());
        return ResponseEntity.ok(Map.of(
                "fingerprint", accountId,
                "accountId", accountId
        ));
    }

    @GetMapping("/language")
    public ResponseEntity<Map<String, Object>> language()
    {
        UiLanguage language = languageSettingsService.current();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("language", language.name());
        payload.put("value", language.name().toLowerCase());
        payload.put("settingsPath", languageSettingsService.settingsPath().toString());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/language")
    public ResponseEntity<Map<String, Object>> updateLanguage(@RequestBody Map<String, String> request)
    {
        String value = request == null ? null : request.get("language");
        UiLanguage language = UiLanguage.fromUserSelection(value);
        if(language == null)
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "language must be one of: english, en, 1, chinese, zh, cn, 2");
            return ResponseEntity.badRequest().body(error);
        }

        languageSettingsService.save(language);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("language", language.name());
        payload.put("value", language.name().toLowerCase());
        payload.put("settingsPath", languageSettingsService.settingsPath().toString());
        payload.put("message", "Language updated");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/key/import-private-file")
    public ResponseEntity<Map<String, Object>> importPrivateKeyFile(@RequestBody ImportPrivateKeyRequest request) throws Exception
    {
        if (request == null || request.getPrivateKeyPath() == null || request.getPrivateKeyPath().isBlank()) {
            throw new IllegalArgumentException("privateKeyPath is required");
        }
        privateKeyArtifactService.importPrivateKey(PathInputNormalizer.toPath(request.getPrivateKeyPath()));
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        return ResponseEntity.ok(cryptoSupport.keyStatus());
    }

    @PostMapping("/key/export-private")
    public ResponseEntity<Map<String, Object>> exportPrivateKey() throws Exception
    {
        PrivateKeyArtifactService.ExportedPrivateKey exported = privateKeyArtifactService.exportPrivateKey();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("privateKey", exported.privateKeyText());
        payload.put("pngPath", exported.artifact().getPngPath().toString());
        payload.put("textPath", exported.artifact().getFst1Path().toString());
        payload.put("asciiPath", exported.artifact().getAsciiPath().toString());
        payload.put("expiresAt", exported.artifact().getExpiresAt().toString());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/startup-status")
    public ResponseEntity<Map<String, Object>> startupStatus()
    {
        return ResponseEntity.ok(clientStartupCoordinator.startupStatus());
    }

    @GetMapping("/auto-connect/status")
    public ResponseEntity<Map<String, Object>> autoConnectStatus()
    {
        return ResponseEntity.ok(clientStartupCoordinator.autoConnectStatus());
    }

    @PostMapping("/auto-connect")
    public ResponseEntity<Map<String, Object>> autoConnect()
    {
        return ResponseEntity.ok(clientStartupCoordinator.triggerAutoConnect());
    }

    @PostMapping("/auto-connect/settings")
    public ResponseEntity<Map<String, Object>> saveAutoConnectSettings(@RequestBody Map<String, Object> request)
    {
        if(request == null || !request.containsKey("enabled"))
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "enabled is required");
            return ResponseEntity.badRequest().body(error);
        }
        Object enabled = request.get("enabled");
        if(!(enabled instanceof Boolean))
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("message", "enabled must be a boolean");
            return ResponseEntity.badRequest().body(error);
        }
        return ResponseEntity.ok(clientStartupCoordinator.saveAutoConnectSettings((Boolean) enabled));
    }

    @PostMapping("/startup/key/generate")
    public ResponseEntity<Map<String, Object>> startupGenerateKey()
    {
        return ResponseEntity.ok(clientStartupCoordinator.generateStartupKeyAndContinue());
    }

    @PostMapping("/startup/key/skip")
    public ResponseEntity<Map<String, Object>> startupSkipKeySetup()
    {
        return ResponseEntity.ok(clientStartupCoordinator.skipStartupKeySetup());
    }

    @PostMapping("/startup/key/import-private")
    public ResponseEntity<Map<String, Object>> startupImportPrivateKey(@RequestBody ImportPrivateKeyRequest request) throws Exception
    {
        importPrivateKey(request);
        return ResponseEntity.ok(clientStartupCoordinator.startupStatus());
    }

    @PostMapping("/startup/key/import-private-file")
    public ResponseEntity<Map<String, Object>> startupImportPrivateKeyFile(@RequestBody ImportPrivateKeyRequest request) throws Exception
    {
        importPrivateKeyFile(request);
        return ResponseEntity.ok(clientStartupCoordinator.startupStatus());
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody(required = false) ConnectRequest request) throws Exception
    {
        String host = (request != null && request.getHost() != null && !request.getHost().isBlank())
                ? request.getHost()
                : clientProperties.getServerHost();
        int port = (request != null && request.getPort() != null)
                ? request.getPort()
                : clientProperties.getServerPort();

        clientConnectionManager.connectAndAuthenticate(host, port)
                .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("host", host);
        payload.put("port", port);
        payload.put("message", "Connected and authenticated");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect()
    {
        clientConnectionManager.disconnect();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", "Disconnected");
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, Object>> shutdown()
    {
        applicationShutdownService.requestShutdownAsync(200L);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("accepted", true);
        payload.put("message", "Shutdown requested");
        return ResponseEntity.accepted().body(payload);
    }

    @GetMapping("/connection-status")
    public ResponseEntity<Map<String, Object>> connectionStatus()
    {
        return ResponseEntity.ok(clientConnectionManager.currentStatus());
    }

    @GetMapping("/user-status")
    public ResponseEntity<Map<String, Object>> userStatus()
    {
        Map<String, Object> connectionStatus = clientConnectionManager.currentStatus();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", connectionStatus.get("deviceId"));
        payload.put("accountId", connectionStatus.get("accountId"));
        payload.put("status", connectionStatus.get("status"));
        payload.put("connected", connectionStatus.get("connected"));
        payload.put("authenticated", connectionStatus.get("authenticated"));
        payload.put("connectedHost", connectionStatus.get("connectedHost"));
        payload.put("connectedPort", connectionStatus.get("connectedPort"));
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey()
    {
        return ResponseEntity.ok(Map.of(
                "publicKey", clientConnectionManager.getLocalPublicKey()
        ));
    }

    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> help()
    {
        Map<String, Object> payload = new LinkedHashMap<>();

        Map<String, String> system = new LinkedHashMap<>();
        system.put("GET /api/system/status", "Show system status");
        system.put("GET /api/system/key", "Show crypto service key status");
        system.put("POST /api/system/key/generate", "Generate key pair in crypto service");
        system.put("POST /api/system/key/delete", "Delete key pair from crypto service");
        system.put("POST /api/system/key/export-private", "Export private key as text and QR artifact files");
        system.put("POST /api/system/key/import-private", "Import private key from raw text, a text file, or a PNG QR (body: {privateKey} or {privateKeyPath})");
        system.put("POST /api/system/key/import-private-file", "Import private key from file or PNG QR (body: {privateKeyPath})");
        system.put("GET /api/system/key/fingerprint", "Calculate fingerprint/accountId for local public key");
        system.put("POST /api/system/key/fingerprint", "Calculate fingerprint for a public key, or local public key when body is empty");
        system.put("GET /api/system/account-id", "Show local accountId");
        system.put("POST /api/system/account-id", "Calculate accountId for a public key, or local public key when body is empty");
        system.put("GET /api/system/language", "Show current console language setting");
        system.put("POST /api/system/language", "Update console language setting (body: {language})");
        system.put("POST /api/system/connect", "Connect to server (optional body: {host, port})");
        system.put("POST /api/system/disconnect", "Disconnect from server");
        system.put("POST /api/system/shutdown", "Request client application shutdown");
        system.put("GET /api/system/connection-status", "Show detailed connection status");
        system.put("GET /api/system/user-status", "Show current user connection status");
        system.put("GET /api/system/public-key", "Show local public key");
        system.put("GET /api/system/startup-status", "Show startup key setup and auto-connect gate status");
        system.put("GET /api/system/auto-connect/status", "Show auto-connect configuration and current gate status");
        system.put("POST /api/system/auto-connect", "Trigger configured auto-connect now");
        system.put("POST /api/system/auto-connect/settings", "Save auto-connect setting (body: {enabled})");
        system.put("POST /api/system/startup/key/generate", "Generate key for startup flow and continue auto-connect if blocked");
        system.put("POST /api/system/startup/key/skip", "Mark startup key setup as skipped");
        system.put("POST /api/system/startup/key/import-private", "Import private key for startup flow from raw text, file, or PNG QR");
        system.put("POST /api/system/startup/key/import-private-file", "Import private key file or PNG QR for startup flow");
        system.put("GET /api/system/help", "Show this help message");
        system.put("GET /host/shutdown", "Open a host-facing shutdown page");
        payload.put("system", system);

        payload.put("send", Map.of(
                "POST /api/send", "Send a file (body: {filePath, targetAccountId})",
                "GET /api/send/tasks", "List all transfer tasks",
                "GET /api/send/tasks/{taskIdOrTransferId}", "Get a specific task details",
                "POST /api/send/tasks/{taskIdOrTransferId}/cancel", "Cancel an active transfer task",
                "GET /api/send/tasks/{taskIdOrTransferId}/events", "Watch a task progress stream with Server-Sent Events"
        ));
        payload.put("messages", Map.of(
                "POST /api/messages/send", "Send an encrypted relay text message (body: {targetAccountId, text})",
                "GET /api/messages", "List in-memory message conversation summaries",
                "GET /api/messages/{accountId}", "Show one in-memory conversation and mark displayed incoming messages as read"
        ));
        payload.put("notifications", Map.of(
                "GET /api/events", "Subscribe to local notification events with Server-Sent Events",
                "GET /api/notifications/events", "Alias of GET /api/events"
        ));
        payload.put("receive", Map.of(
                "GET /incoming", "List incoming transfer requests",
                "POST /accept", "Accept an incoming transfer (body: {transferId})",
                "POST /reject", "Reject an incoming transfer (body: {transferId})",
                "POST /retransmit", "Request retransmission for a receive task (body: {taskIdOrTransferId} or {transferId})",
                "GET /retransmit-requests", "List pending retransmission requests waiting for sender confirmation",
                "POST /retransmit-accept", "Accept a pending retransmission request (body: {transferId})",
                "POST /retransmit-reject", "Reject a pending retransmission request (body: {transferId})",
                "POST /open-received", "Reveal a received file (body: {taskIdOrTransferId}, {transferId}, {taskId}, {target}, or {fileName})"
        ));
        return ResponseEntity.ok(payload);
    }
}
