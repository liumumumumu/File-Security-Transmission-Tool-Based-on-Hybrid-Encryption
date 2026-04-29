package com.controller;

import com.common.config.ClientProperties;
import com.common.config.CryptoServiceProperties;
import com.common.config.NodeProperties;
import com.common.config.ServerProperties;
import com.controller.dto.ImportPrivateKeyRequest;
import com.crypto.CryptoSupport;
import com.service.LocalTransferHistoryService;
import com.service.TransferTaskRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public SystemController(
            ClientProperties clientProperties,
            ServerProperties serverProperties,
            CryptoServiceProperties cryptoServiceProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            LocalTransferHistoryService localTransferHistoryService
    )
    {
        this.clientProperties = clientProperties;
        this.serverProperties = serverProperties;
        this.cryptoServiceProperties = cryptoServiceProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.transferTaskRegistry = transferTaskRegistry;
        this.localTransferHistoryService = localTransferHistoryService;
    }

    @GetMapping("/status")
    public Map<String, Object> status()
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("application", "file-security-transmission-tool");
        payload.put("status", "UP");
        payload.put("clientServerHost", clientProperties.getServerHost());
        payload.put("clientServerPort", clientProperties.getServerPort());
        payload.put("tcpBindHost", serverProperties.getBindHost());
        payload.put("tcpBindPort", serverProperties.getBindPort());
        payload.put("cryptoServiceAddress", cryptoServiceProperties.getAddress());
        payload.put("cryptoServicePort", cryptoServiceProperties.getPort());
        return payload;
    }

    @GetMapping("/key")
    public ResponseEntity<Map<String, Object>> key() throws Exception
    {
        return ResponseEntity.ok(cryptoSupport.keyStatus());
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
}
