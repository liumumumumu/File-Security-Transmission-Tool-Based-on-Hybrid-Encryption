package com.service;

import com.persistence.mapper.AuthLogMapper;
import com.persistence.mapper.DeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
}
