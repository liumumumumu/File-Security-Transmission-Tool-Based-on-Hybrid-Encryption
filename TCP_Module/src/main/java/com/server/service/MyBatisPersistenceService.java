package com.server.service;

import com.persistence.local.mapper.transmissionRecord.AuthLogMapper;
import com.persistence.local.mapper.transmissionRecord.DeviceMapper;
import com.persistence.local.model.transmissionRecord.AuthLogRecord;
import com.persistence.local.model.transmissionRecord.DeviceRecord;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;

/**
 * Author: LQH
 * Date: 2026-05-01
 * Purpose: 负责服务器端MySQL持久化记录，设备在线/ 离线记录，认证日志
 *
 * */

@Service
@Slf4j
public class MyBatisPersistenceService
{
    private final DeviceMapper deviceMapper;
    private final AuthLogMapper authLogMapper;

    public MyBatisPersistenceService(AuthLogMapper authLogMapper, DeviceMapper deviceMapper) {
        this.authLogMapper = authLogMapper;
        this.deviceMapper = deviceMapper;
    }

    //把设备的在线信息更新到MySQL
    public void upsertOnlineDevice(String deviceId, String publicKey)
    {
        DeviceRecord record = new DeviceRecord(deviceId, LocalDateTime.now(), publicKey, "ONLINE");
        deviceMapper.upsert(record);
    }

    //标记设备离线
    public void markDeviceOffline(String deviceId)
    {
        deviceMapper.updateStatus(deviceId, "OFFLINE", LocalDateTime.now());
    }

    //记录一次认证成功日志
    public void logAuthSuccess(String deviceId, String publicKey, String challengeId, Channel channel)
    {
        insertAuthLog(deviceId, publicKey, challengeId, channel, "SUCCESS", null);
    }

    //记录一次认证失败日志
    public void logAuthFailure(String deviceId, String publicKey, String challengeId, Channel channel, String failureReason)
    {
        insertAuthLog(deviceId, publicKey, challengeId, channel, "FAILED", failureReason);
    }

    //静默处理设备离线
    public void markDeviceOfflineQuietly(String deviceId)
    {
        try
        {
            markDeviceOffline(deviceId);
        }
        catch(Exception e)
        {
            log.info("Failed to mark device offline in MySQL, deviceId={}", deviceId, e);
        }
    }

    //插入一条认证日志
    private void insertAuthLog(String deviceId, String publicKey, String challengeId, Channel channel, String result, String failureReason)
    {
        AuthLogRecord record=new AuthLogRecord(challengeId,clientIp(channel),LocalDateTime.now(),deviceId,failureReason,publicKey,result);
        authLogMapper.insert(record);
    }

    //获取客户端的Ip
    private String clientIp(Channel channel)
    {
        if(channel == null || !(channel.remoteAddress() instanceof InetSocketAddress address))
        {
            return "";
        }
        if(address.getAddress()!=null)
        {
            return address.getAddress().getHostAddress();
        }
        return address.getHostString();
    }
}
