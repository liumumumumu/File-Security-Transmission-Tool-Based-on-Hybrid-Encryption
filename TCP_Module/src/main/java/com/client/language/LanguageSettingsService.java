package com.client.language;

import com.common.config.LocalStorageProperties;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LanguageSettingsService
{
    private final Path settingsPath;
    private final Gson gson = new Gson();

    public LanguageSettingsService(LocalStorageProperties localStorageProperties)
    {
        this(Path.of(localStorageProperties.getLanguageSettingsPath()).toAbsolutePath());
    }

    public LanguageSettingsService(Path settingsPath)
    {
        this.settingsPath = settingsPath.toAbsolutePath();
    }

    public synchronized UiLanguage current()
    {
        if(Files.notExists(settingsPath))
        {
            return UiLanguage.defaultLanguage();
        }
        try
        {
            LanguageSettings settings = gson.fromJson(Files.readString(settingsPath), LanguageSettings.class);
            return settings == null ? UiLanguage.defaultLanguage() : UiLanguage.fromStoredValue(settings.language);
        }
        catch(IOException | JsonSyntaxException ex)
        {
            return UiLanguage.defaultLanguage();
        }
    }

    public synchronized void save(UiLanguage language)
    {
        UiLanguage selected = language == null ? UiLanguage.defaultLanguage() : language;
        try
        {
            Path parent = settingsPath.getParent();
            if(parent != null)
            {
                Files.createDirectories(parent);
            }
            Files.writeString(settingsPath, gson.toJson(new LanguageSettings(selected.name())));
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Unable to write language settings: " + settingsPath, ex);
        }
    }

    public Path settingsPath()
    {
        return settingsPath;
    }

    private static class LanguageSettings
    {
        private String language;

        private LanguageSettings(String language)
        {
            this.language = language;
        }
    }
}
