package com.client.service;

import com.common.config.LocalSqliteMyBatisConfig;
import com.common.config.LocalStorageProperties;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        classes = LocalContactBookServiceBlacklistTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("client")
class LocalContactBookServiceBlacklistTest
{
    @TempDir
    static Path tempDir;

    @Autowired
    private LocalContactBookService localContactBookService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry)
    {
        registry.add("app.local-storage.sqlite-path", () -> tempDir.resolve("contacts-test.db").toString());
        registry.add("app.local-storage.transfer-history-path", () -> tempDir.resolve("transfer-history.json").toString());
        registry.add("app.local-storage.device-id-path", () -> tempDir.resolve("device-id").toString());
        registry.add("app.local-storage.startup-state-path", () -> tempDir.resolve("startup-state.json").toString());
    }

    @Test
    void addBlacklistReturnsPersistedRecord()
    {
        BlacklistRecord record = localContactBookService.addBlacklist("blocked-user", null, "spam");

        assertNotNull(record);
        assertEquals("blocked-user", record.getAccountId());
        assertEquals("spam", record.getReason());
        assertTrue(localContactBookService.isBlacklisted("blocked-user"));
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(LocalStorageProperties.class)
    @Import(LocalContactBookService.class)
    static class TestConfig extends LocalSqliteMyBatisConfig
    {
    }
}
