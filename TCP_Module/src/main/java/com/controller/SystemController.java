package com.controller;

import com.common.config.ClientProperties;
import com.common.config.CryptoServiceProperties;
import com.common.config.ServerProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController
{
    private final ClientProperties clientProperties;
    private final ServerProperties serverProperties;
    private final CryptoServiceProperties cryptoServiceProperties;

    public SystemController(
            ClientProperties clientProperties,
            ServerProperties serverProperties,
            CryptoServiceProperties cryptoServiceProperties
    )
    {
        this.clientProperties = clientProperties;
        this.serverProperties = serverProperties;
        this.cryptoServiceProperties = cryptoServiceProperties;
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
}
