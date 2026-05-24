package com.client.language;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class LanguageSettingsServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void currentDefaultsToEnglishWhenFileDoesNotExist()
    {
        LanguageSettingsService service = new LanguageSettingsService(settingsPath());

        assertEquals(UiLanguage.ENGLISH, service.current());
    }

    @Test
    public void savePersistsSelectedLanguage()
    {
        Path settingsPath = settingsPath();
        LanguageSettingsService service = new LanguageSettingsService(settingsPath);

        service.save(UiLanguage.CHINESE);

        assertEquals(UiLanguage.CHINESE, new LanguageSettingsService(settingsPath).current());
    }

    @Test
    public void currentFallsBackToEnglishWhenJsonIsInvalid() throws Exception
    {
        Path settingsPath = settingsPath();
        Files.writeString(settingsPath, "{not-json");
        LanguageSettingsService service = new LanguageSettingsService(settingsPath);

        assertEquals(UiLanguage.ENGLISH, service.current());
    }

    @Test
    public void currentFallsBackToEnglishWhenLanguageIsUnsupported() throws Exception
    {
        Path settingsPath = settingsPath();
        Files.writeString(settingsPath, "{\"language\":\"FRENCH\"}");
        LanguageSettingsService service = new LanguageSettingsService(settingsPath);

        assertEquals(UiLanguage.ENGLISH, service.current());
    }

    private Path settingsPath()
    {
        return temporaryFolder.getRoot().toPath().resolve("language-settings.json");
    }
}
