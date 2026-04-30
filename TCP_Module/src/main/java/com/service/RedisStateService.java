package com.service;

import com.common.config.RedisStateProperties;
import com.server.PendingAuthChallenge;
import com.server.TransferRoute;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisStateService
{
    //Redis key前缀，用来给不同类型数据分类
    private static final String AUTH_CHALLENGE_PREFIX = "auth:challenge:";  //用来保存设备认证时的临时Challenge
    private static final String ONLINE_DEVICE_PREFIX = "online:device:";    //用来记录某个设备当前是否在线
    private static final String ROUTE_TRANSFER_PREFIX = "route:transfer:";  //用来保存某个文件传输任务的路由信息
    private static final String ROUTE_DEVICE_PREFIX = "route:device:";      //用来按设备反查它参与了哪些传输任务


    private final StringRedisTemplate stringRedisTemplate;
    private final RedisStateProperties redisStateProperties;

    public RedisStateService(RedisStateProperties redisStateProperties, StringRedisTemplate stringRedisTemplate) {
        this.redisStateProperties = redisStateProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void saveChallenge(PendingAuthChallenge challenge)
    {
        String key=challengeKey(challenge.getChallengeId());
        stringRedisTemplate.opsForHash().putAll(
                key, Map.of(
                        "deviceId", challenge.getDeviceId(),
                        "publicKey", challenge.getPublicKey(),
                        "challengeId", challenge.getChallengeId(),
                        "challenge", challenge.getChallenge(),
                        "createdAt", Instant.now().toString()
                )
        );
        stringRedisTemplate.expire(key, redisStateProperties.getChallengeTtlSeconds(), TimeUnit.SECONDS);
    }

    public Optional<PendingAuthChallenge> takeChallenge(String challengeId)
    {
        String key=challengeKey(challengeId);
        Map<Object, Object>values = stringRedisTemplate.opsForHash().entries(key);
        stringRedisTemplate.delete(key);
        if(values == null || values.isEmpty())
        {
            return Optional.empty();
        }

        return Optional.of(
                new PendingAuthChallenge(
                        stringValue(values.get("deviceId")),
                        stringValue(values.get("publicKey")),
                        stringValue(values.get("challengeId")),
                        stringValue(values.get("challenge"))
                )
        );
    }

    public void saveOnlineSession(String deviceId, String publicKey, String channelId)
    {
        String key=onlineDeviceKey(deviceId);
        String now=Instant.now().toString();
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                "deviceId", deviceId,
                "publicKey", publicKey,
                "channelId", channelId,
                "connectedAt", now,
                "lastHeartbeatAt", now
        ));
        stringRedisTemplate.expire(key, redisStateProperties.getOnlineSessionTtlSeconds(), TimeUnit.SECONDS);
    }

    public void touchOnlineSession(String deviceId)
    {
        String key=onlineDeviceKey(deviceId);
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)))
        {
            stringRedisTemplate.opsForHash().put(key, "lastHeartbeatAt", Instant.now().toString());
            stringRedisTemplate.expire(key, redisStateProperties.getOnlineSessionTtlSeconds(), TimeUnit.SECONDS);
        }
    }

    public void removeOnlineSession(String deviceId)
    {
        stringRedisTemplate.delete(onlineDeviceKey(deviceId));
    }

    public void saveTransferRoute(TransferRoute route)
    {
        String routeKey=routeTransferKey(route.getTransferId());
        stringRedisTemplate.opsForHash().putAll(routeKey, Map.of(
                "transferId", route.getTransferId(),
                "senderDeviceId", route.getSenderDeviceId(),
                "receiverDeviceId", route.getReceiverDeviceId(),
                "createdAt", Instant.now().toString()
        ));
        stringRedisTemplate.expire(routeKey, redisStateProperties.getTransferRouteTtlSeconds(), TimeUnit.SECONDS);

        addRouteIndex(route.getSenderDeviceId(), route.getTransferId());
        addRouteIndex(route.getReceiverDeviceId(), route.getTransferId());
    }

    public Optional<TransferRoute> findTransferRoute(String transferId)
    {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(routeTransferKey(transferId));
        if(values == null || values.isEmpty())
        {
            return Optional.empty();
        }

        return Optional.of(new TransferRoute(
                stringValue(values.get("transferId")),
                stringValue(values.get("senderDeviceId")),
                stringValue(values.get("receiverDeviced"))
        ));
    }

    public void removeTransferRoute(String transferId)
    {
        Optional<TransferRoute> route = findTransferRoute(transferId);
        stringRedisTemplate.delete(routeTransferKey(transferId));
        route.ifPresent(value->{
            stringRedisTemplate.opsForSet().remove(routeDeviceKey(value.getSenderDeviceId()), transferId);
            stringRedisTemplate.opsForSet().remove(routeDeviceKey(value.getReceiverDeviceId()), transferId);
        });
    }

    public void removeRoutesForDevice(String deviceId)
    {
        String deviceKey = routeDeviceKey(deviceId);
        Set<String> transferIds = stringRedisTemplate.opsForSet().members(deviceKey);
        if(transferIds != null)
        {
            for(String transferId : transferIds)
            {
                removeTransferRoute(transferId);
            }
        }
        stringRedisTemplate.delete(deviceKey);
    }

    private void addRouteIndex(String deviceId, String transferId)
    {
        String key=routeDeviceKey(deviceId);
        stringRedisTemplate.opsForSet().add(key, transferId);
        stringRedisTemplate.expire(key, redisStateProperties.getTransferRouteTtlSeconds(), TimeUnit.SECONDS);
    }

    private String challengeKey(String challengeId)
    {
        return this.AUTH_CHALLENGE_PREFIX + challengeId;
    }

    private String onlineDeviceKey(String deviceId)
    {
        return this.ONLINE_DEVICE_PREFIX + deviceId;
    }

    private String routeTransferKey(String transferId)
    {
        return this.ROUTE_TRANSFER_PREFIX + transferId;
    }

    private String routeDeviceKey(String deviceId)
    {
        return this.ROUTE_DEVICE_PREFIX + deviceId;
    }

    private String stringValue(Object value)
    {
        return value == null ? null : value.toString();
    }

}
