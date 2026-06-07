package com.client.language;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConsoleMessagesTest
{
    @Test
    public void allMessageKeysHaveEnglishAndChineseText()
    {
        assertTrue(ConsoleMessages.missingTranslations().isEmpty());
    }

    @Test
    public void formatsMessagesWithArguments()
    {
        assertEquals(
                "Connected and authenticated: localhost:9000",
                ConsoleMessages.format(UiLanguage.ENGLISH, ConsoleMessages.Key.CONNECTED_AUTHENTICATED, "localhost", 9000)
        );
        assertEquals(
                "已连接并完成认证: localhost:9000",
                ConsoleMessages.format(UiLanguage.CHINESE, ConsoleMessages.Key.CONNECTED_AUTHENTICATED, "localhost", 9000)
        );
    }

    @Test
    public void translatesLabelsButKeepsEnglishLabelsForEnglish()
    {
        assertEquals("status", ConsoleMessages.label(UiLanguage.ENGLISH, "status"));
        assertEquals("状态", ConsoleMessages.label(UiLanguage.CHINESE, "status"));
    }
}
