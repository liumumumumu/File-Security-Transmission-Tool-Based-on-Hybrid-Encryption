package com.persistence.local.mapper.contactsRecord;

import com.persistence.local.model.contactsRecord.BlacklistRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BlacklistMapper
{
    @Update("""
            CREATE TABLE IF NOT EXISTS blacklist (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                account_id TEXT NOT NULL UNIQUE,
                public_key TEXT,
                reason TEXT,
                created_at TEXT NOT NULL
            )
            """)
    void createTableIfNotExists();

    @Insert("""
            INSERT INTO blacklist (
                account_id,
                public_key,
                reason,
                created_at
            )
            VALUES (
                #{accountId},
                #{publicKey},
                #{reason},
                #{createdAt}
            )
            ON CONFLICT(account_id) DO UPDATE SET
                public_key = excluded.public_key,
                reason = excluded.reason
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(BlacklistRecord record);

    @Select("""
            SELECT
                id,
                account_id AS accountId,
                public_key AS publicKey,
                reason,
                created_at AS createdAt
            FROM blacklist
            ORDER BY created_at DESC
            """)
    List<BlacklistRecord> findAll();

    @Select("""
            SELECT
                id,
                account_id AS accountId,
                public_key AS publicKey,
                reason,
                created_at AS createdAt
            FROM blacklist
            WHERE account_id = #{accountId}
            """)
    BlacklistRecord findByAccountId(@Param("accountId") String accountId);

    @Delete("""
            DELETE FROM blacklist
            WHERE account_id = #{accountId}
            """)
    int deleteByAccountId(@Param("accountId") String accountId);
}
