package com.controller;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.config.CryptoServiceProperties;
import com.common.config.NodeProperties;
import com.common.config.ServerProperties;
import com.controller.dto.ConnectRequest;
import com.controller.dto.ImportPrivateKeyRequest;
import com.crypto.CryptoSupport;
import com.service.LocalTransferHistoryService;
import com.service.TransferTaskRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
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
    private final TransferTaskRegistry transferTaskRegistry;
    private final LocalTransferHistoryService localTransferHistoryService;
    private final ClientConnectionManager clientConnectionManager;

    public SystemController(
            ClientProperties clientProperties,
            ServerProperties serverProperties,
            CryptoServiceProperties cryptoServiceProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            LocalTransferHistoryService localTransferHistoryService,
            ClientConnectionManager clientConnectionManager
    )
    {
        this.clientProperties = clientProperties;
        this.serverProperties = serverProperties;
        this.cryptoServiceProperties = cryptoServiceProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.transferTaskRegistry = transferTaskRegistry;
        this.localTransferHistoryService = localTransferHistoryService;
        this.clientConnectionManager = clientConnectionManager;
    }

    @GetMapping("/status")
    public Map<String, Object> status() throws Exception
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", "file-security-transmission-tool");
        payload.put("status", "UP");
        payload.put("deviceId", nodeProperties.getDeviceId());
        payload.put("accountId", cryptoSupport.publicKeyFingerprint());
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
        return ResponseEntity.ok(cryptoSupport.generateKeyPair());
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
            cryptoSupport.importPrivateKeyText(request.getPrivateKey());
        } else if (request.getPrivateKeyPath() != null && !request.getPrivateKeyPath().isBlank()) {
            cryptoSupport.importPrivateKeyFile(Path.of(request.getPrivateKeyPath()));
        } else {
            throw new IllegalArgumentException("privateKey or privateKeyPath is required");
        }
        return ResponseEntity.ok(cryptoSupport.keyStatus());
    }

    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody(required = false) ConnectRequest request) throws Exception
    {
        String host = (request != null && request.getHost() != null) ? request.getHost() : clientProperties.getServerHost();
        int port = (request != null && request.getPort() != null) ? request.getPort() : clientProperties.getServerPort();

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

    @GetMapping("/connection-status")
    public ResponseEntity<Map<String, Object>> connectionStatus()
    {
        return ResponseEntity.ok(clientConnectionManager.currentStatus());
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> publicKey()
    {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("publicKey", clientConnectionManager.getLocalPublicKey());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> help()
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("system", Map.of(
                "GET /api/system/status", "Show system and connection status",
                "GET /api/system/key", "Show crypto service key status",
                "POST /api/system/key/generate", "Generate key pair in crypto service",
                "POST /api/system/key/delete", "Delete key pair from crypto service",
                "POST /api/system/key/import-private", "Import private key (body: {privateKey} or {privateKeyPath})",
                "POST /api/system/connect", "Connect to server (optional body: {host, port})",
                "POST /api/system/disconnect", "Disconnect from server",
                "GET /api/system/connection-status", "Show detailed connection status",
                "GET /api/system/public-key", "Show local public key",
                "GET /api/system/help", "Show this help message"
        ));
        payload.put("send", Map.of(
                "POST /api/send", "Send a file (body: {filePath, targetAccountId, targetDeviceId})",
                "GET /api/send/tasks", "List all transfer tasks",
                "GET /api/send/tasks/{taskIdOrTransferId}", "Get a specific task details"
        ));
        payload.put("receive", Map.of(
                "GET /api/receive/incoming", "List incoming transfer requests",
                "POST /api/receive/accept", "Accept an incoming transfer (body: {transferId})",
                "POST /api/receive/reject", "Reject an incoming transfer (body: {transferId})"
        ));
        return ResponseEntity.ok(payload);
    }
}
