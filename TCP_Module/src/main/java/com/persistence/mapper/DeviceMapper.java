package com.persistence.mapper;

import com.persistence.model.DeviceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMapper
{
    void upsert(DeviceRecord record);
    void updateStatus();
}
