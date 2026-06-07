package com.client.service;

import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.QrArtifactService;
import com.common.util.PathInputNormalizer;
import com.crypto.CryptoSupport;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PrivateKeyArtifactService
{
    private static final Duration EXPORT_TTL = Duration.ofHours(24);

    private final CryptoSupport cryptoSupport;
    private final QrArtifactService qrArtifactService;

    public PrivateKeyArtifactService(CryptoSupport cryptoSupport, QrArtifactService qrArtifactService)
    {
        this.cryptoSupport = cryptoSupport;
        this.qrArtifactService = qrArtifactService;
    }

    public ExportedPrivateKey exportPrivateKey() throws GeneralSecurityException
    {
        String privateKeyText = cryptoSupport.getEncodedPrivateKey();
        String qrText = KeyArtifactPayload.privateArtifact(privateKeyText);
        Instant expiresAt = Instant.now().plus(EXPORT_TTL);
        QrArtifact artifact = qrArtifactService.writeArtifacts("private-key", UUID.randomUUID().toString(), expiresAt, qrText, ".fstpriv");
        return new ExportedPrivateKey(privateKeyText, qrText, artifact);
    }

    public void importPrivateKey(String privateKeyTextOrPath)
    {
        Path path = resolveExistingPath(privateKeyTextOrPath);
        if(path != null)
        {
            importPrivateKey(path);
            return;
        }
        cryptoSupport.importPrivateKeyText(normalizePrivateKeyText(privateKeyTextOrPath));
    }

    public void importPrivateKey(Path privateKeyPath)
    {
        Path normalizedPath = privateKeyPath.toAbsolutePath().normalize();
        cryptoSupport.importPrivateKeyText(readPrivateKey(normalizedPath));
    }

    public String readPrivateKey(Path path)
    {
        return normalizePrivateKeyText(qrArtifactService.readQrText(path));
    }

    public String normalizePrivateKeyText(String text)
    {
        String normalized = KeyArtifactPayload.extractPrivateKey(text);
        if(KeyArtifactPayload.containsPemEnvelope(normalized, "PRIVATE KEY"))
        {
            return KeyArtifactPayload.normalizePemEnvelope(normalized, "PRIVATE KEY");
        }
        return normalized.trim();
    }

    private Path resolveExistingPath(String value)
    {
        if(value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            Path path = PathInputNormalizer.toPath(value);
            if(Files.exists(path))
            {
                return path;
            }
        }
        catch(Exception ignored)
        {
        }
        return null;
    }

    public record ExportedPrivateKey(String privateKeyText,
                                     String qrText,
                                     QrArtifact artifact)
    {
    }
}
