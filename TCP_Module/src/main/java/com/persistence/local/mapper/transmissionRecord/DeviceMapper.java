package com.persistence.local.mapper.transmissionRecord;

import com.persistence.local.model.transmissionRecord.DeviceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface DeviceMapper
{
    @Insert("""
            INSERT INTO device (device_id, public_key, status, last_seen_at, created_at, updated_at)
            VALUES (#{deviceId}, #{publicKey}, #{status}, #{lastSeenAt}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                public_key = VALUES(public_key),
                status = VALUES(status),
                last_seen_at = VALUES(last_seen_at),
                updated_at = NOW()
            """)
    void upsert(DeviceRecord record);//update+insert

    @Update("""
            UPDATE device
            SET status = #{status},
                last_seen_at = #{lastSeenAt},
                updated_at = NOW()
            WHERE device_id = #{deviceId}
            """)
    void updateStatus(@Param("deviceId") String deviceId, @Param("status") String status, @Param("lastSeenAt")LocalDateTime lastSeenAt);
}
