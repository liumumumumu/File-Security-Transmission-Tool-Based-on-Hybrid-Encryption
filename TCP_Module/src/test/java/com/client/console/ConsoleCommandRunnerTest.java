package com.client.console;

import com.client.ApplicationShutdownService;
import com.client.language.ConsoleMessages;
import com.client.language.LanguageSettingsService;
import com.client.service.PrivateKeyArtifactService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.support.GenericApplicationContext;

<<<<<<< HEAD
=======
import java.util.List;

import static org.junit.Assert.assertEquals;
>>>>>>> origin/main
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConsoleCommandRunnerTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void handleConsoleInputClosedClosesApplicationContext()
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        ConsoleCommandRunner runner = new ConsoleCommandRunner(
                null,
                null,
                null,
                null,
                null,
                null,
                shutdownService(applicationContext),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
<<<<<<< HEAD
                null,
=======
>>>>>>> origin/main
                messages()
        );

        runner.handleConsoleInputClosed();

        assertFalse(applicationContext.isActive());
    }

    @Test
    public void handleConsoleInputClosedIsSafeWhenContextAlreadyClosed()
    {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.refresh();
        applicationContext.close();
        ConsoleCommandRunner runner = new ConsoleCommandRunner(
                null,
                null,
                null,
                null,
                null,
                null,
                shutdownService(applicationContext),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
<<<<<<< HEAD
                null,
=======
>>>>>>> origin/main
                messages()
        );

        runner.handleConsoleInputClosed();

        assertTrue(!applicationContext.isActive());
    }

<<<<<<< HEAD
=======
    @Test
    public void wrapLongTextSplitsTextIntoTerminalSafeLines()
    {
        String wrapped = ConsoleCommandRunner.wrapLongText("FST1:1234567890", 6);

        assertEquals("FST1:1" + System.lineSeparator() + "234567" + System.lineSeparator() + "890", wrapped);
    }

    @Test
    public void joinFst1PasteLinesRemovesLineBreaksAndOuterWhitespace()
    {
        String joined = ConsoleCommandRunner.joinFst1PasteLines(List.of(
                "FST1:abc",
                "  def  ",
                "ghi"
        ));

        assertEquals("FST1:abcdefghi", joined);
    }

>>>>>>> origin/main
    private ConsoleMessages messages()
    {
        return new ConsoleMessages(new LanguageSettingsService(
                temporaryFolder.getRoot().toPath().resolve("language-settings.json")));
    }

    private ApplicationShutdownService shutdownService(GenericApplicationContext applicationContext)
    {
        return new ApplicationShutdownService(applicationContext, messages());
    }
}
