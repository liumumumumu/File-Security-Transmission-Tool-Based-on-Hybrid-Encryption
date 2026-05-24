package com.client;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientUiLauncherTest
{
    @Test
    void buildsLocalUiUriFromClientHttpAddressAndPort()
    {
        URI uri = ClientUiLauncher.localUiUri("127.0.0.1", 20201);

        assertEquals(URI.create("http://127.0.0.1:20201/"), uri);
    }

    @Test
    void usesLoopbackAddressWhenServerBindsAllInterfaces()
    {
        URI uri = ClientUiLauncher.localUiUri("0.0.0.0", 20201);

        assertEquals(URI.create("http://127.0.0.1:20201/"), uri);
    }
}
