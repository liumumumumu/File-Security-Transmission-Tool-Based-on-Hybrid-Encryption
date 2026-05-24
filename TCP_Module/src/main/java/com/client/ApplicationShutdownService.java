package com.client;

import com.client.language.ConsoleMessages;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ApplicationShutdownService
{
    private final ConfigurableApplicationContext applicationContext;
    private final ConsoleMessages messages;
    private final AtomicBoolean shutdownAnnounced = new AtomicBoolean(false);

    public ApplicationShutdownService(ConfigurableApplicationContext applicationContext, ConsoleMessages messages)
    {
        this.applicationContext = applicationContext;
        this.messages = messages;
    }

    public void requestShutdown()
    {
        if (!applicationContext.isActive()) {
            return;
        }
        if (shutdownAnnounced.compareAndSet(false, true)) {
            System.out.println(messages == null
                    ? "Stopping application..."
                    : messages.text(ConsoleMessages.Key.STOPPING_APPLICATION));
        }
        applicationContext.close();
    }

    public void requestShutdownAsync(long delayMillis)
    {
        long effectiveDelayMillis = Math.max(0L, delayMillis);
        Thread thread = new Thread(() -> {
            if (effectiveDelayMillis > 0L) {
                try {
                    Thread.sleep(effectiveDelayMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            requestShutdown();
        }, "application-shutdown-request");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isApplicationActive()
    {
        return applicationContext.isActive();
    }
}
