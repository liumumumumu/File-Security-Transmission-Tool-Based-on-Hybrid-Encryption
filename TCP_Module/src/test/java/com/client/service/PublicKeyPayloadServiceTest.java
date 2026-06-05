package com.client.service;

import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.QrArtifactService;
import com.common.config.LocalStorageProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PublicKeyPayloadServiceTest
{
    private static final String PUBLIC_KEY_BASE64 = "YWJjZA==";
    private static final String PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\nYWJjZA==\n-----END PUBLIC KEY-----\n";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsFstpubPublicKeyArtifact() throws Exception
    {
        PublicKeyPayloadService service = service();
        Path fstpub = temporaryFolder.getRoot().toPath().resolve("receiver.fstpub");
        Files.writeString(fstpub, "FST-PUB1:" + PUBLIC_KEY_BASE64);

        String publicKey = service.resolvePublicKey(fstpub.toString());

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void readsGeneratedFstpubPublicKeyArtifact()
    {
        PublicKeyPayloadService service = service();
        QrArtifact artifact = qrArtifactService().writePermanentArtifacts(
                "public-key",
                "pub-1234",
                KeyArtifactPayload.publicArtifact(PUBLIC_KEY),
                ".fstpub"
        );

        String publicKey = service.resolvePublicKey(artifact.getFst1Path().toString());

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsLegacyFstPubArtifact()
    {
        PublicKeyPayloadService service = service();

        String publicKey = service.normalizePublicKeyText("FST-PUB1:" + PUBLIC_KEY_BASE64);

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsUnifiedPublicKeyArtifact()
    {
        PublicKeyPayloadService service = service();

        String publicKey = service.normalizePublicKeyText(KeyArtifactPayload.publicArtifact(PUBLIC_KEY));

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsConsolePublicKeyLine()
    {
        PublicKeyPayloadService service = service();

        String publicKey = service.normalizePublicKeyText("publicKey: " + PUBLIC_KEY_BASE64);

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsConsoleQrTextLine()
    {
        PublicKeyPayloadService service = service();

        String publicKey = service.normalizePublicKeyText("qrText: FST-PUB1:" + PUBLIC_KEY_BASE64);

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsExportPublicKeyJsonResponse()
    {
        PublicKeyPayloadService service = service();

        String publicKey = service.normalizePublicKeyText("{\"success\":true,\"qrText\":\"FST-PUB1:" + PUBLIC_KEY_BASE64 + "\"}");

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void acceptsPemPublicKeyEnvelope()
    {
        PublicKeyPayloadService service = service();
        String pem = "-----BEGIN PUBLIC KEY-----\nYWJj\nZA==\n-----END PUBLIC KEY-----";

        String publicKey = service.normalizePublicKeyText(pem);

        assertEquals(PUBLIC_KEY, publicKey);
    }

    @Test
    public void rejectsWrongFstArtifactType()
    {
        PublicKeyPayloadService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.normalizePublicKeyText("FST1:not-a-public-key"));
        assertThrows(IllegalArgumentException.class, () -> service.normalizePublicKeyText(KeyArtifactPayload.privateArtifact("private-key")));
    }

    private PublicKeyPayloadService service()
    {
        return new PublicKeyPayloadService(null, null, qrArtifactService());
    }

    private QrArtifactService qrArtifactService()
    {
        LocalStorageProperties properties = new LocalStorageProperties();
        properties.setQrOutputDir(temporaryFolder.getRoot().toPath().resolve("qr-output").toString());
        return new QrArtifactService(properties);
    }
}
