package com.persistence.local.mapper.transmissionRecord;

import com.persistence.local.model.transmissionRecord.AuthLogRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthLogMapper
{
    @Insert("""
            INSERT INTO auth_log (
                device_id,
                public_key,
                challenge_id,
                client_ip,
                result,
                failure_reason,
                created_at
            )
            VALUES (
                #{deviceId},
                #{publicKey},
                #{challengeId},
                #{clientIp},
                #{result},
                #{failureReason},
                #{createdAt}
            )
            """)
    void insert(AuthLogRecord record);
}
