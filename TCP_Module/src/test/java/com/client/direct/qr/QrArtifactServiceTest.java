package com.client.direct.qr;

import com.common.config.LocalStorageProperties;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
<<<<<<< HEAD
import static org.junit.Assert.assertThrows;
=======
>>>>>>> origin/main

public class QrArtifactServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readFst1TextSupportsQuotedFst1FilePath() throws Exception
    {
        QrArtifactService service = service();
        Path fst1 = temporaryFolder.getRoot().toPath().resolve("quoted path.fst1");
        Files.writeString(fst1, "FST1:test-value");

        String value = service.readFst1Text("\"" + fst1 + "\"");

        assertEquals("FST1:test-value", value);
    }

    @Test
    public void readFst1TextSupportsPngFileUri() throws Exception
    {
        QrArtifactService service = service();
        QrArtifact artifact = service.writeArtifacts("sender", "invite-1234", Instant.now().plusSeconds(60), "FST1:test-value");

        String value = service.readFst1Text(artifact.getPngPath().toUri().toString());

        assertEquals("FST1:test-value", value);
    }

    @Test
<<<<<<< HEAD
    public void writeArtifactsWritesFst1TextAsSingleLine() throws Exception
    {
        QrArtifactService service = service();

        QrArtifact artifact = service.writeArtifacts(
                "sender",
                "invite-1234",
                Instant.now().plusSeconds(60),
                "FST1:test \nvalue\twrapped"
        );

        assertEquals("FST1:test valuewrapped", Files.readString(artifact.getFst1Path()));
    }

    @Test
    public void readFst1TextRejectsDirectTextInput()
    {
        QrArtifactService service = service();

        assertThrows(IllegalArgumentException.class, () -> service.readFst1Text("FST1:test-value"));
    }

    @Test
    public void readFst1TextSupportsWrappedFst1FilePath() throws Exception
    {
        QrArtifactService service = service();
        Path fst1 = temporaryFolder.getRoot().toPath().resolve("wrapped.fst1");
        Files.writeString(fst1, "FST1:test \nvalue\twrapped");

        String value = service.readFst1Text(fst1.toString());

        assertEquals("FST1:test valuewrapped", value);
=======
    public void readFst1TextSupportsWrappedPastedText()
    {
        QrArtifactService service = service();

        String value = service.readFst1Text("FST1:test-\n value\twrapped");

        assertEquals("FST1:test-valuewrapped", value);
>>>>>>> origin/main
    }

    private QrArtifactService service()
    {
        LocalStorageProperties properties = new LocalStorageProperties();
        properties.setQrOutputDir(temporaryFolder.getRoot().toPath().resolve("qr-output").toString());
        return new QrArtifactService(properties);
    }
}
