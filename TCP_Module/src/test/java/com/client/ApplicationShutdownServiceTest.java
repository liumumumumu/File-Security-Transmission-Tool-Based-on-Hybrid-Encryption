package com.client;

import com.client.language.ConsoleMessages;
import com.client.language.LanguageSettingsService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.Assert.assertFalse;

public class ApplicationShutdownServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void requestShutdownClosesApplicationContext()
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ApplicationShutdownService service = new ApplicationShutdownService(applicationContext, messages());

        service.requestShutdown();

        assertFalse(applicationContext.isActive());
    }

    @Test
    public void requestShutdownIsIdempotent()
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ApplicationShutdownService service = new ApplicationShutdownService(applicationContext, messages());

        service.requestShutdown();
        service.requestShutdown();

        assertFalse(applicationContext.isActive());
    }

    private ConsoleMessages messages()
    {
        return new ConsoleMessages(new LanguageSettingsService(
                temporaryFolder.getRoot().toPath().resolve("language-settings.json")));
    }
}
