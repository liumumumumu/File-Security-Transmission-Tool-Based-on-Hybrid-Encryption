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


/**
 * Author: LQH
 * Date: 2026-04-30
 * Purpose: 用于服务端将短期运行状态存进Redis的工具类
 * 管理三类状态：1.认证Challenge; 2.在线设备会话; 3.文件传输路由
 *
 * */

@Service
public class RedisStateService
{
    //Redis key前缀，用来给不同类型数据分类
    private static final String AUTH_CHALLENGE_PREFIX = "auth:challenge:";  //用来保存设备认证时的Challenge的验证方，challenge内容的信息
    private static final String ONLINE_DEVICE_PREFIX = "online:device:";    //用来保存某个在线设备的相关信息
    private static final String ROUTE_TRANSFER_PREFIX = "route:transfer:";  //用来保存某个文件传输任务的路由信息
    private static final String ROUTE_DEVICE_PREFIX = "route:device:";      //用来保存某个设备参与的哪些传输任务


    private final StringRedisTemplate stringRedisTemplate;//Spring提供的Redis操作客户端，真正发生读写Redis的操作
    private final RedisStateProperties redisStateProperties;//配置属性对象

    public RedisStateService(RedisStateProperties redisStateProperties, StringRedisTemplate stringRedisTemplate) {
        this.redisStateProperties = redisStateProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //将认证用的challenge临时保存到Redis，并设置过期时间
    public void saveChallenge(PendingAuthChallenge challenge)
    {
        String key=challengeKey(challenge.getChallengeId());//根据ChallengeId,生成Redis key
        stringRedisTemplate.opsForHash().putAll(            //用来操作Hash类数据
                key, Map.of(
                        "deviceId", challenge.getDeviceId(),
                        "publicKey", challenge.getPublicKey(),
                        "challengeId", challenge.getChallengeId(),
                        "challenge", challenge.getChallenge(),
                        "createdAt", Instant.now().toString()
                )
        );//使用Redis的Hash结构保存数据
        stringRedisTemplate.expire(key, redisStateProperties.getChallengeTtlSeconds(), TimeUnit.SECONDS);//设置Challenge的有效时间
    }

    //根据ChallengeId从Redis取出认证Challenge，并且取完就删除
    //可能取到PendingAuthChallenge，也可能取不到
    public Optional<PendingAuthChallenge> takeChallenge(String challengeId)
    {
        String key=challengeKey(challengeId);//拼出Redis的key
        Map<Object, Object>values = stringRedisTemplate.opsForHash().entries(key);//从Redis读取这个Hash里面的所有字段
        stringRedisTemplate.delete(key);//读完即删除，确保Challenge是一次性使用的
        if(values == null || values.isEmpty())//Redis里面没有数据的情况
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
        );//取到数据，以PendingAuthChallenge对象返回
    }

    //把设备的在线会话状态保存到Redis，并设置在线状态过期时间
    public void saveOnlineSession(String deviceId, String publicKey, String channelId)
    {
        String key=onlineDeviceKey(deviceId);//根据deviceId生成Redis key
        String now=Instant.now().toString();//当前时间
        stringRedisTemplate.opsForHash().putAll(key, Map.of(
                "deviceId", deviceId,
                "publicKey", publicKey,
                "channelId", channelId,
                "connectedAt", now,
                "lastHeartbeatAt", now
        ));//用Redis Hash保存在线设备信息
        stringRedisTemplate.expire(key, redisStateProperties.getOnlineSessionTtlSeconds(), TimeUnit.SECONDS);//过期时间
    }

    //刷新在线设备的心跳时间，重置过期时间计时
    public void touchOnlineSession(String deviceId)
    {
        String key=onlineDeviceKey(deviceId);//获取设备在Redis里面的键值
        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(key)))//检查是否存在这个在线设别的键值
        {
            stringRedisTemplate.opsForHash().put(key, "lastHeartbeatAt", Instant.now().toString());//更新Redis Hash里的lastHeartbeatAt字段
            stringRedisTemplate.expire(key, redisStateProperties.getOnlineSessionTtlSeconds(), TimeUnit.SECONDS);//重置过期时间计时
        }
    }

    //删除某个设备的在线状态
    public void removeOnlineSession(String deviceId)
    {
        stringRedisTemplate.delete(onlineDeviceKey(deviceId));
    }

    //保存文件传输的路由关系
    public void saveTransferRoute(TransferRoute route)
    {
        String routeKey=routeTransferKey(route.getTransferId());
        stringRedisTemplate.opsForHash().putAll(routeKey, Map.of(
                "transferId", route.getTransferId(),
                "senderDeviceId", route.getSenderDeviceId(),
                "receiverDeviceId", route.getReceiverDeviceId(),
                "createdAt", Instant.now().toString()
        ));//以Redis Hash的方式保存传输路由
        stringRedisTemplate.expire(routeKey, redisStateProperties.getTransferRouteTtlSeconds(), TimeUnit.SECONDS);//传输路由的过期时间

        //设备到传输任务的索引
        addRouteIndex(route.getSenderDeviceId(), route.getTransferId());//根据发送方的设备Id查寻参与了哪些传输任务
        addRouteIndex(route.getReceiverDeviceId(), route.getTransferId());//根据接收方的设备Id查寻参与了哪些传输任务
    }

    //根据transferId从Redis里查除文件的传输路由
    public Optional<TransferRoute> findTransferRoute(String transferId)
    {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(routeTransferKey(transferId));//根据传输任务Id从Redis Hash取出相关的路由信息
        if(values == null || values.isEmpty())
        {
            return Optional.empty();
        }

        return Optional.of(new TransferRoute(
                stringValue(values.get("senderDeviceId")),
                stringValue(values.get("transferId")),
                stringValue(values.get("receiverDeviceId"))
        ));//以TransferRoute对象的形式返回传输路由的信息
    }

    //删除某个传输任务的路由信息
    public void removeTransferRoute(String transferId)
    {
        Optional<TransferRoute> route = findTransferRoute(transferId);//根据transferId拿到该传输任务的路由信息
        stringRedisTemplate.delete(routeTransferKey(transferId));
        route.ifPresent(value->{
            stringRedisTemplate.opsForSet().remove(routeDeviceKey(value.getSenderDeviceId()), transferId);
            stringRedisTemplate.opsForSet().remove(routeDeviceKey(value.getReceiverDeviceId()), transferId);
        });//再从两个设备索引表里面移除该transferId
    }

    //删除某个设备参与的所有传输路由
    public void removeRoutesForDevice(String deviceId)
    {
        String deviceKey = routeDeviceKey(deviceId);//生成设备对应的Redis Set key
        Set<String> transferIds = stringRedisTemplate.opsForSet().members(deviceKey);//从Set中取出这个设备关联的所有传输Id;opsForSet操作Redis的Set类型
        if(transferIds != null)
        {
            for(String transferId : transferIds)//逐个调用removeTransferRoute，来删除传输路由的信息
            {
                removeTransferRoute(transferId);
            }
        }
        stringRedisTemplate.delete(deviceKey);//删除设备索引
    }

    //给某个设备建立它参与的传输任务索引表
    private void addRouteIndex(String deviceId, String transferId)
    {
        String key=routeDeviceKey(deviceId);//该设备的路由索引Redis key
        stringRedisTemplate.opsForSet().add(key, transferId);//把这个传输任务Id加入该设备对应的传输任务集合
        stringRedisTemplate.expire(key, redisStateProperties.getTransferRouteTtlSeconds(), TimeUnit.SECONDS);//给该设备索引设置过期时间
    }

    //根据challengeId拼出Redis里保存的challenge key
    private String challengeKey(String challengeId)
    {
        return this.AUTH_CHALLENGE_PREFIX + challengeId;
    }

    //根据deviceId拼出Redis里保存的在线设备状态key
    private String onlineDeviceKey(String deviceId)
    {
        return this.ONLINE_DEVICE_PREFIX + deviceId;
    }

    //根据transferId拼出Redis里保存的传输路由记录key
    private String routeTransferKey(String transferId)
    {
        return this.ROUTE_TRANSFER_PREFIX + transferId;
    }

    //根据deviceId拼出Redis里保存的设备参与传输任务索key
    private String routeDeviceKey(String deviceId)
    {
        return this.ROUTE_DEVICE_PREFIX + deviceId;
    }

    private String stringValue(Object value)//将Object转成String类型
    {
        return value == null ? null : value.toString();
    }

}
