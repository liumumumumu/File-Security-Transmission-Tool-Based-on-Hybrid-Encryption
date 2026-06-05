package com.client.service;

import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.QrArtifactService;
import com.common.util.PathInputNormalizer;
import com.crypto.CryptoSupport;
import com.persistence.local.model.contactsRecord.ContactRecord;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class PublicKeyPayloadService
{
    public static final String PREFIX = KeyArtifactPayload.LEGACY_PUBLIC_PREFIX;

    private final CryptoSupport cryptoSupport;
    private final LocalContactBookService localContactBookService;
    private final QrArtifactService qrArtifactService;

    public PublicKeyPayloadService(CryptoSupport cryptoSupport,
                                   LocalContactBookService localContactBookService,
                                   QrArtifactService qrArtifactService)
    {
        this.cryptoSupport = cryptoSupport;
        this.localContactBookService = localContactBookService;
        this.qrArtifactService = qrArtifactService;
    }

    public String resolvePublicKey(String value)
    {
        if(value == null || value.isBlank())
        {
            throw new IllegalArgumentException("public key, public key path, or contact index is required");
        }
        Optional<Integer> contactIndex = parseContactToken(value.trim());
        if(contactIndex.isPresent())
        {
            ContactRecord contact = localContactBookService.findContactByIndex(contactIndex.get())
                    .orElseThrow(() -> new IllegalArgumentException("Contact not found: contact-"+contactIndex.get()));
            if(contact.getPublicKey() == null || contact.getPublicKey().isBlank())
            {
                throw new IllegalArgumentException("Contact publicKey is empty: contact-"+contactIndex.get());
            }
            return normalizePublicKeyText(contact.getPublicKey());
        }

        Path path = resolveExistingPath(value);
        if(path != null)
        {
            return readPublicKey(path);
        }
        return normalizePublicKeyText(value);
    }

    public String resolvePublicKey(String publicKey, String publicKeyPath, Integer contactIndex)
    {
        int supplied = 0;
        supplied += publicKey != null && !publicKey.isBlank() ? 1 : 0;
        supplied += publicKeyPath != null && !publicKeyPath.isBlank() ? 1 : 0;
        supplied += contactIndex != null ? 1 : 0;
        if(supplied != 1)
        {
            throw new IllegalArgumentException("Exactly one receiver public key source is required");
        }
        if(publicKey != null && !publicKey.isBlank())
        {
            return normalizePublicKeyText(publicKey);
        }
        if(publicKeyPath != null && !publicKeyPath.isBlank())
        {
            return readPublicKey(PathInputNormalizer.toPath(publicKeyPath));
        }
        return resolvePublicKey("contact-"+contactIndex);
    }

    public String readPublicKey(Path path)
    {
        String text = qrArtifactService.readQrText(path);
        return normalizePublicKeyText(text);
    }

    public String normalizePublicKeyText(String text)
    {
        String normalized = KeyArtifactPayload.extractPublicKey(text);
        if(KeyArtifactPayload.containsPemEnvelope(normalized, "PUBLIC KEY"))
        {
            return KeyArtifactPayload.normalizePemEnvelope(normalized, "PUBLIC KEY");
        }
        normalized = KeyArtifactPayload.removeWhitespace(normalized);
        validateBase64(normalized);
        return KeyArtifactPayload.pemFromBase64(normalized, "PUBLIC KEY");
    }

    public ExportedPublicKey exportPublicKey() throws GeneralSecurityException
    {
        String publicKey = cryptoSupport.getEncodedPublicKey();
        String qrText = KeyArtifactPayload.publicArtifact(publicKey);
        QrArtifact artifact = qrArtifactService.writePermanentArtifacts("public-key", UUID.randomUUID().toString(), qrText, ".fstpub");
        return new ExportedPublicKey(publicKey, qrText, artifact);
    }

    public String accountIdForPublicKey(String publicKey) throws GeneralSecurityException
    {
        return cryptoSupport.publicKeyFingerprint(normalizePublicKeyText(publicKey));
    }

    private Optional<Integer> parseContactToken(String value)
    {
        String normalized = value;
        if(normalized.startsWith("contact-"))
        {
            normalized = normalized.substring("contact-".length());
        }
        if(!normalized.matches("\\d+"))
        {
            return Optional.empty();
        }
        int index = Integer.parseInt(normalized);
        if(index <= 0)
        {
            throw new IllegalArgumentException("contact index must be positive: "+value);
        }
        return Optional.of(index);
    }

    private Path resolveExistingPath(String value)
    {
        try
        {
            Path path = PathInputNormalizer.toPath(value);
            return Files.exists(path) ? path : null;
        }
        catch(Exception ignored)
        {
            return null;
        }
    }

    private void validateBase64(String value)
    {
        if(value.isBlank())
        {
            throw new IllegalArgumentException("public key is required");
        }
        try
        {
            Base64.getDecoder().decode(value);
        }
        catch(IllegalArgumentException ex)
        {
            throw new IllegalArgumentException("public key is not valid Base64", ex);
        }
    }

    public record ExportedPublicKey(String publicKey,
                                    String qrText,
                                    QrArtifact artifact)
    {
        public Instant expiresAt()
        {
            return artifact.getExpiresAt();
        }
    }
}
