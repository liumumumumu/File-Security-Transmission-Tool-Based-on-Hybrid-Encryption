package com.client.controller;

import com.client.ApplicationShutdownService;
import com.client.language.LanguageSettingsService;
import com.client.language.UiLanguage;
import com.client.service.PrivateKeyArtifactService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SystemControllerTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shutdownReturnsAcceptedResponse() throws Exception
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ApplicationShutdownService shutdownService = new ApplicationShutdownService(applicationContext, null);
        SystemController controller = new SystemController(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                shutdownService,
                null,
                null
        );

        ResponseEntity<Map<String, Object>> response = controller.shutdown();

        assertEquals(202, response.getStatusCode().value());
        assertTrue(Boolean.TRUE.equals(response.getBody().get("accepted")));
        assertEquals("Shutdown requested", response.getBody().get("message"));
    }

    @Test
    public void languageEndpointsReadAndUpdateSetting()
    {
        LanguageSettingsService languageSettingsService = new LanguageSettingsService(
                temporaryFolder.getRoot().toPath().resolve("language-settings.json")
        );
        SystemController controller = new SystemController(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                languageSettingsService
        );

        ResponseEntity<Map<String, Object>> updateResponse = controller.updateLanguage(Map.of("language", "zh"));
        ResponseEntity<Map<String, Object>> readResponse = controller.language();

        assertEquals(200, updateResponse.getStatusCode().value());
        assertEquals(true, updateResponse.getBody().get("success"));
        assertEquals(UiLanguage.CHINESE.name(), readResponse.getBody().get("language"));
    }
}
