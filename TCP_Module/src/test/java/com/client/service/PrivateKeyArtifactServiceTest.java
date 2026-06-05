package com.client.service;

import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.QrArtifactService;
import com.common.config.CryptoServiceProperties;
import com.common.config.LocalStorageProperties;
import com.crypto.CryptoSupport;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PrivateKeyArtifactServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportPrivateKeyCreatesQrArtifacts() throws Exception
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        PrivateKeyArtifactService.ExportedPrivateKey exported = service.exportPrivateKey();

        assertEquals("private-key-text", exported.privateKeyText());
        assertEquals("private-key-text", service.normalizePrivateKeyText(exported.qrText()));
        assertTrue(Files.exists(exported.artifact().getPngPath()));
        assertTrue(Files.exists(exported.artifact().getFst1Path()));
        assertTrue(exported.artifact().getFst1Path().toString().endsWith(".fstpriv"));
        assertTrue(Files.exists(exported.artifact().getAsciiPath()));
    }

    @Test
    public void importPrivateKeyReadsPrivateKeyFromPng() throws Exception
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);
        QrArtifact artifact = service.exportPrivateKey().artifact();

        service.importPrivateKey(artifact.getPngPath());

        assertEquals("private-key-text", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyReadsExistingTextFilePathAndNormalizesContent() throws Exception
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);
        Path path = temporaryFolder.getRoot().toPath().resolve("private-key.txt");
        Files.writeString(path, KeyArtifactPayload.privateArtifact("file-private-key"));

        service.importPrivateKey(path);

        assertEquals("file-private-key", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyTreatsNonPathInputAsRawText()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        service.importPrivateKey("plain-private-key");

        assertEquals("plain-private-key", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyAcceptsLabeledText()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        service.importPrivateKey("privateKey: labeled-private-key");

        assertEquals("labeled-private-key", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyAcceptsLegacyPrivatePrefix()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        service.importPrivateKey("FST-PRIV1:legacy-private-key");

        assertEquals("legacy-private-key", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyPreservesPemShape()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);
        String pem = "-----BEGIN PRIVATE KEY-----\nYWJjZA==\n-----END PRIVATE KEY-----";

        service.importPrivateKey(KeyArtifactPayload.privateArtifact(pem));

        assertEquals("-----BEGIN PRIVATE KEY-----\nYWJjZA==\n-----END PRIVATE KEY-----\n", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyAcceptsConsoleQrTextLine()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        service.importPrivateKey("qrText: " + KeyArtifactPayload.privateArtifact("private-key"));

        assertEquals("private-key", cryptoSupport.importedPrivateKeyText);
    }

    @Test
    public void importPrivateKeyRejectsPublicArtifact()
    {
        FakeCryptoSupport cryptoSupport = new FakeCryptoSupport("private-key-text");
        PrivateKeyArtifactService service = service(cryptoSupport);

        assertThrows(IllegalArgumentException.class, () -> service.importPrivateKey(KeyArtifactPayload.publicArtifact("public-key")));
    }

    private PrivateKeyArtifactService service(FakeCryptoSupport cryptoSupport)
    {
        LocalStorageProperties properties = new LocalStorageProperties();
        properties.setQrOutputDir(temporaryFolder.getRoot().toPath().resolve("qr-output").toString());
        return new PrivateKeyArtifactService(cryptoSupport, new QrArtifactService(properties));
    }

    private static class FakeCryptoSupport extends CryptoSupport
    {
        private final String privateKeyText;
        private String importedPrivateKeyText;

        private FakeCryptoSupport(String privateKeyText)
        {
            super(new CryptoServiceProperties());
            this.privateKeyText = privateKeyText;
        }

        @Override
        public String getEncodedPrivateKey()
        {
            return privateKeyText;
        }

        @Override
        public synchronized void importPrivateKeyText(String privateKeyText)
        {
            this.importedPrivateKeyText = privateKeyText;
        }

        @Override
        public synchronized void importPrivateKeyFile(Path privateKeyPath)
        {
            throw new AssertionError("Private key file imports should be normalized before calling crypto service");
        }
    }
}
