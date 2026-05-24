package com.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.env.Environment;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

@Component
@Slf4j
public class ClientUiLauncher
{
    private static final int DEFAULT_CLIENT_HTTP_PORT = 20201;

    private final Environment environment;

    public ClientUiLauncher(Environment environment)
    {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openUiAfterStartup()
    {
        boolean enabled = environment.getProperty("app.ui.open-browser", Boolean.class, true);
        if (!enabled) {
            log.info("UI browser auto-launch is disabled.");
            return;
        }

        String address = environment.getProperty("server.address", "127.0.0.1");
        int port = environment.getProperty("server.port", Integer.class, DEFAULT_CLIENT_HTTP_PORT);
        URI uiUri = localUiUri(address, port);

        Thread launcherThread = new Thread(() -> openBrowser(uiUri), "client-ui-launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
    }

    static URI localUiUri(String serverAddress, int port)
    {
        return URI.create("http://" + localBrowserHost(serverAddress) + ":" + port + "/");
    }

    private static String localBrowserHost(String serverAddress)
    {
        String host = serverAddress == null ? "" : serverAddress.trim();
        if (host.isEmpty() || "0.0.0.0".equals(host) || "::".equals(host) || "[::]".equals(host)) {
            return "127.0.0.1";
        }
        if (host.contains(":") && !host.startsWith("[")) {
            return "[" + host + "]";
        }
        return host;
    }

    private void openBrowser(URI uiUri)
    {
        try {
            Thread.sleep(600L);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uiUri);
                log.info("Opened UI in default browser: {}", uiUri);
                return;
            }

            openBrowserWithPlatformCommand(uiUri);
            log.info("Opened UI with platform command: {}", uiUri);
        } catch (Exception ex) {
            log.warn("Unable to open UI automatically. Open {} manually.", uiUri, ex);
            System.out.println("Open UI manually: " + uiUri);
        }
    }

    private void openBrowserWithPlatformCommand(URI uiUri) throws Exception
    {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String url = uiUri.toString();

        ProcessBuilder processBuilder;
        if (osName.contains("win")) {
            processBuilder = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (osName.contains("mac")) {
            processBuilder = new ProcessBuilder("open", url);
        } else {
            processBuilder = new ProcessBuilder("xdg-open", url);
        }
        processBuilder.start();
    }
}
