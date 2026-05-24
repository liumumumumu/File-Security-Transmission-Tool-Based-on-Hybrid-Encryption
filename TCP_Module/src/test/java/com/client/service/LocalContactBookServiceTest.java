package com.client.service;

import com.persistence.local.mapper.contactsRecord.BlacklistMapper;
import com.persistence.local.mapper.contactsRecord.ContactMapper;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LocalContactBookServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void addBlacklistReturnsPersistedRecord() throws Exception
    {
        try(SqlSession session = sqlSessionFactory().openSession(true))
        {
            LocalContactBookService service = new LocalContactBookService(
                    session.getMapper(BlacklistMapper.class),
                    session.getMapper(ContactMapper.class)
            );
            service.initTables();

            BlacklistRecord record = service.addBlacklist("account-1", "public-key-1", "spam");

            assertNotNull(record);
            assertEquals("account-1", record.getAccountId());
            assertEquals("public-key-1", record.getPublicKey());
            assertEquals("spam", record.getReason());
            assertTrue(service.isBlacklisted("account-1"));
        }
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception
    {
        Path sqlitePath = temporaryFolder.newFile("contacts.db").toPath();
        PooledDataSource dataSource = new PooledDataSource(
                "org.sqlite.JDBC",
                "jdbc:sqlite:" + sqlitePath,
                null,
                null
        );
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(ContactMapper.class);
        configuration.addMapper(BlacklistMapper.class);
        return new SqlSessionFactoryBuilder().build(configuration);
    }
}
