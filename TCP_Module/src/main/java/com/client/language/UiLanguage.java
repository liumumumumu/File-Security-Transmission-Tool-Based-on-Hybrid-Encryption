package com.client.language;

import java.util.Locale;

public enum UiLanguage
{
    ENGLISH,
    CHINESE;

    public static UiLanguage defaultLanguage()
    {
        return ENGLISH;
    }

    public static UiLanguage fromStoredValue(String value)
    {
        if(value == null || value.isBlank())
        {
            return defaultLanguage();
        }
        try
        {
            return UiLanguage.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch(IllegalArgumentException ex)
        {
            return defaultLanguage();
        }
    }

    public static UiLanguage fromUserSelection(String value)
    {
        if(value == null)
        {
            return null;
        }
        return switch(value.trim().toLowerCase(Locale.ROOT))
        {
            case "1", "english", "en" -> ENGLISH;
            case "2", "chinese", "zh", "cn" -> CHINESE;
            default -> null;
        };
    }
}
