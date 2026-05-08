package com.persistence.local.mapper.contactsRecord;

import com.persistence.local.model.contactsRecord.ContactRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ContactMapper
{
    @Update("""
            CREATE TABLE IF NOT EXISTS contact (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_index INTEGER NOT NULL UNIQUE,
                alias TEXT,
                account_id TEXT NOT NULL UNIQUE,
                public_key TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """)
    void createTableIfNotExists();

    @Select("""
            SELECT COALESCE(MAX(contact_index), 0) + 1
            FROM contact
            """)
    Integer nextContactIndex();

    @Insert("""
            INSERT INTO contact (
                contact_index,
                alias,
                account_id,
                public_key,
                created_at,
                updated_at
            )
            VALUES (
                #{contactIndex},
                #{alias},
                #{accountId},
                #{publicKey},
                #{createdAt},
                #{updatedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ContactRecord record);

    @Update("""
            UPDATE contact
            SET alias = #{alias},
                public_key = #{publicKey},
                updated_at = #{updatedAt}
            WHERE account_id = #{accountId}
            """)
    int updateByAccountId(ContactRecord record);

    @Select("""
            SELECT
                id,
                contact_index AS contactIndex,
                alias,
                account_id AS accountId,
                public_key AS publicKey,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM contact
            ORDER BY contact_index ASC
            """)
    List<ContactRecord> findAll();

    @Select("""
            SELECT
                id,
                contact_index AS contactIndex,
                alias,
                account_id AS accountId,
                public_key AS publicKey,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM contact
            WHERE contact_index = #{contactIndex}
            """)
    ContactRecord findByContactIndex(@Param("contactIndex") int contactIndex);


    @Select("""
            SELECT
                id,
                contact_index AS contactIndex,
                alias,
                account_id AS accountId,
                public_key AS publicKey,
                created_at AS createdAt,
                updated_at AS updatedAt
            FROM contact
            WHERE account_id = #{accountId}
            """)
    ContactRecord findByAccountId(@Param("accountId") String accountId);

    @Delete("""
            DELETE FROM contact
            WHERE contact_index = #{contactIndex}
            """)
    int deleteByContactIndex(@Param("contactIndex") int contactIndex);

    @Delete("""
            DELETE FROM contact
            WHERE account_id = #{accountId}
            """)
    int deleteByAccountId(@Param("accountId") String accountId);
}
