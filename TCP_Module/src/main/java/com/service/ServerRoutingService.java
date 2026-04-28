package com.service;


import com.common.config.AuthenticationResultProperties;
import com.crypto.CryptoSupport;
import com.server.ServerClientSession;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ServerRoutingService
{
    private final CryptoSupport cryptoSupport;
    private final AuthenticationResultProperties authenticationResultProperties;
    private final PushNotificationService pushNotificationService;
    private final RedisStateService redisStateService;
    private final MyBatisPersistenceService myBatisPersistenceService;

    private final Map<String, ServerClientSession> sessionsByDeviceId=new ConcurrentHashMap<>();
    private final Map<Channel, String> deviceIdByChannel=new ConcurrentHashMap<>();


    public ServerRoutingService(AuthenticationResultProperties authenticationResultProperties, CryptoSupport cryptoSupport, PushNotificationService pushNotificationService, RedisStateService redisStateService, MyBatisPersistenceService myBatisPersistenceService) {
        this.authenticationResultProperties = authenticationResultProperties;
        this.cryptoSupport = cryptoSupport;
        this.pushNotificationService = pushNotificationService;
        this.redisStateService = redisStateService;
        this.myBatisPersistenceService = myBatisPersistenceService;
    }
}
