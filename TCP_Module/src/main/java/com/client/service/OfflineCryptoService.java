package com.client.service;

import com.client.direct.qr.CborLite;
import com.common.config.TransferProperties;
import com.common.crypto.AesGcmChunk;
import com.common.util.PathInputNormalizer;
import com.crypto.CryptoSupport;
import org.springframework.stereotype.Service;

import javax.crypto.BadPaddingException;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Author: LQH
 * Date: 2026-06-26
 * Update: 2026-07-03 — 加宽异常捕获防止未处理异常导致 500
 * Purpose: 离线加解密核心服务，实现 FST2 (文件) 和 FST-TEXT1 (文本) 两种自定协议。
 *          FST2: AES-256-GCM 分块加密 + RSA 密钥封装 + HMAC-SHA256 nonce 派生
 *          FST-TEXT1: AES-256-GCM 文本加密 + CBOR 序列化 + Base64URL 编码
 */
@Service
public class OfflineCryptoService
{
    private static final byte[] FST2_MAGIC = new byte[]{'F', 'S', 'T', '2'};
    private static final byte FST2_VERSION = 1;
    private static final byte ALG_KEY_WRAP_RSA = 1;
    private static final byte ALG_AES_256_GCM = 1;
    private static final byte ALG_NONCE_HMAC_SHA256 = 1;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;
    private static final int NONCE_SEED_BYTES = 32;
    private static final int MAX_TEXT_BYTES = 16 * 1024;
    private static final String FST_TEXT_PREFIX = "FST-TEXT1:";

    private final CryptoSupport cryptoSupport;
    private final TransferProperties transferProperties;
    private final PublicKeyPayloadService publicKeyPayloadService;
    private final SecureRandom secureRandom = new SecureRandom();

    public OfflineCryptoService(CryptoSupport cryptoSupport,
                                TransferProperties transferProperties,
                                PublicKeyPayloadService publicKeyPayloadService)
    {
        this.cryptoSupport = cryptoSupport;
        this.transferProperties = transferProperties;
        this.publicKeyPayloadService = publicKeyPayloadService;
    }

    public Fst2EncryptResult encryptFile(Path sourcePath, String receiverPublicKeyToken, Path outputDir)
    {
        try
        {
            Path source = sourcePath.toAbsolutePath().normalize();
            if(Files.notExists(source))
            {
                throw new IllegalArgumentException("File does not exist: "+source);
            }
            if(Files.isDirectory(source))
            {
                throw new IllegalArgumentException("Directories are not supported yet; please compress the directory into a file first.");
            }
            if(!Files.isRegularFile(source))
            {
                throw new IllegalArgumentException("Path is not a regular file: "+source);
            }
            Path targetDir = resolveOutputDir(outputDir);
            Files.createDirectories(targetDir);

            long fileSize = Files.size(source);
            int chunkSize = transferProperties.getChunkSizeBytes();
            int totalBlocks = computeTotalBlocks(fileSize, chunkSize);
            String receiverPublicKey = publicKeyPayloadService.resolvePublicKey(receiverPublicKeyToken);
            SecretKey aesKey = cryptoSupport.generateAESKey();
            byte[] encryptedSessionKey = Base64.getDecoder().decode(cryptoSupport.encryptAESKeyForReceiver(aesKey, receiverPublicKey));
            byte[] nonceSeed = randomBytes(NONCE_SEED_BYTES);

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("fileName", source.getFileName().toString());
            header.put("fileSize", fileSize);
            header.put("chunkSizeBytes", chunkSize);
            header.put("totalBlocks", totalBlocks);
            byte[] headerPlain = CborLite.encodeCanonical(header);
            int headerCiphertextLength = headerPlain.length;
            byte[] headerAad = fst2HeaderAad(encryptedSessionKey, nonceSeed, headerCiphertextLength);
            AesGcmChunk encryptedHeader = cryptoSupport.encryptChunk(headerPlain, aesKey, deriveNonce(nonceSeed, "FST2-header", 0), headerAad);

            UUID artifactId = UUID.randomUUID();
            Path tempPath = targetDir.resolve(".fst2-encrypt-" + artifactId + ".tmp");
            Path finalPath = targetDir.resolve(artifactId + ".FST2");
            try
            {
                try(DataOutputStream out = new DataOutputStream(Files.newOutputStream(
                        tempPath,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)))
                {
                    writeFst2Prefix(out);
                    writeLengthBytes(out, encryptedSessionKey);
                    writeLengthBytes(out, nonceSeed);
                    writeLengthBytes(out, encryptedHeader.ciphertext());
                    writeLengthBytes(out, encryptedHeader.tag());
                    writeEncryptedBlocks(source, out, aesKey, nonceSeed, chunkSize);
                }
                moveCompletedTempFile(tempPath, finalPath);
            }
            catch(Exception ex)
            {
                Files.deleteIfExists(tempPath);
                throw ex;
            }
            return new Fst2EncryptResult(true, finalPath, finalPath.getFileName().toString(), fileSize, totalBlocks);
        }
        catch(Exception ex)
        {
            throw new IllegalStateException("FST2 file encryption failed: "+ex.getMessage(), ex);
        }
    }

    public Fst2DecryptResult decryptFile(Path fst2Path, Path outputDir)
    {
        Path tempPath = null;
        try
        {
            Path source = fst2Path.toAbsolutePath().normalize();
            if(Files.notExists(source))
            {
                throw new IllegalArgumentException("FST2 file does not exist: "+source);
            }
            Path targetDir = resolveOutputDir(outputDir);
            Files.createDirectories(targetDir);

            try(DataInputStream in = new DataInputStream(Files.newInputStream(source)))
            {
                readAndValidateFst2Prefix(in);
                byte[] encryptedSessionKey = readLengthBytes(in, "encryptedSessionKey");
                byte[] nonceSeed = readLengthBytes(in, "nonceSeed");
                if(nonceSeed.length != NONCE_SEED_BYTES)
                {
                    throw new IllegalArgumentException("Invalid FST2 nonce seed length");
                }
                int headerCiphertextLength = readNonNegativeLength(in, "headerCiphertextLength");
                byte[] headerCiphertext = in.readNBytes(headerCiphertextLength);
                if(headerCiphertext.length != headerCiphertextLength)
                {
                    throw new EOFException("FST2 header is truncated");
                }
                byte[] headerTag = readLengthBytes(in, "headerTag");
                if(headerTag.length != GCM_TAG_BYTES)
                {
                    throw new IllegalArgumentException("Invalid FST2 header tag length");
                }

                SecretKey aesKey = decryptSessionKey(encryptedSessionKey);
                byte[] headerAad = fst2HeaderAad(encryptedSessionKey, nonceSeed, headerCiphertextLength);
                byte[] headerPlain = cryptoSupport.decryptChunk(
                        deriveNonce(nonceSeed, "FST2-header", 0),
                        headerCiphertext,
                        headerTag,
                        aesKey,
                        headerAad
                );
                Map<String, Object> header = CborLite.decodeMap(headerPlain);
                String originalFileName = stringValue(header, "fileName");
                long fileSize = longValue(header, "fileSize");
                int chunkSize = intValue(header, "chunkSizeBytes");
                int totalBlocks = intValue(header, "totalBlocks");
                validateHeaderValues(fileSize, chunkSize, totalBlocks);

                String safeFileName = sanitizeFileName(originalFileName);
                Path finalPath = uniqueOutputPath(targetDir, safeFileName);
                tempPath = targetDir.resolve(".fst2-decrypt-" + UUID.randomUUID() + ".tmp");
                long writtenBytes = 0;
                try(OutputStream out = Files.newOutputStream(tempPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
                {
                    for(int expectedBlock = 0; expectedBlock < totalBlocks; expectedBlock++)
                    {
                        int blockIndex = in.readInt();
                        int plaintextLength = in.readInt();
                        int ciphertextLength = readNonNegativeLength(in, "ciphertextLength");
                        if(blockIndex != expectedBlock)
                        {
                            throw new IllegalArgumentException("FST2 block index mismatch");
                        }
                        validatePlaintextLength(fileSize, chunkSize, totalBlocks, expectedBlock, plaintextLength);
                        byte[] ciphertext = in.readNBytes(ciphertextLength);
                        if(ciphertext.length != ciphertextLength)
                        {
                            throw new EOFException("FST2 block ciphertext is truncated");
                        }
                        byte[] tag = readLengthBytes(in, "blockTag");
                        if(tag.length != GCM_TAG_BYTES)
                        {
                            throw new IllegalArgumentException("Invalid FST2 block tag length");
                        }
                        byte[] aad = fst2BlockAad(blockIndex, plaintextLength, ciphertextLength);
                        byte[] plain = cryptoSupport.decryptChunk(
                                deriveNonce(nonceSeed, "FST2-block", blockIndex),
                                ciphertext,
                                tag,
                                aesKey,
                                aad
                        );
                        if(plain.length != plaintextLength)
                        {
                            throw new IllegalArgumentException("FST2 plaintext length mismatch");
                        }
                        out.write(plain);
                        writtenBytes += plain.length;
                    }
                    if(in.read() != -1)
                    {
                        throw new IllegalArgumentException("FST2 contains trailing bytes");
                    }
                    if(writtenBytes != fileSize)
                    {
                        throw new IllegalArgumentException("FST2 final byte count mismatch");
                    }
                }
                moveCompletedTempFile(tempPath, finalPath);
                tempPath = null;
                return new Fst2DecryptResult(true, finalPath, safeFileName, fileSize, totalBlocks);
            }
        }
        catch(Exception ex)
        {
            Throwable cause = ex;
            while(cause != null)
            {
                if(cause instanceof BadPaddingException)
                {
                    throw new IllegalStateException(
                            "Decryption failed: The local private key does not match the public key "
                                    + "used for encryption (RSA-OAEP padding validation failed).\n\n"
                                    + "Common causes:\n"
                                    + "1. You recently reinstalled the software, which generated a new key pair, "
                                    + "but the sender is still encrypting with your old public key.\n"
                                    + "2. You imported an incorrect private key.\n\n"
                                    + "Solutions:\n"
                                    + "- Ask the sender to obtain your new public key (export from app settings) "
                                    + "and re-encrypt the content.\n"
                                    + "- If you have a backup of your old private key, restore it via 'Import Private Key'.",
                            ex);
                }
                cause = cause.getCause();
            }
            throw new IllegalStateException("FST2 file decryption failed: "+ex.getMessage(), ex);
        }
        finally
        {
            if(tempPath != null)
            {
                try
                {
                    Files.deleteIfExists(tempPath);
                }
                catch(IOException ignored)
                {
                }
            }
        }
    }

    public FstTextEncryptResult encryptText(String text, String receiverPublicKeyToken)
    {
        try
        {
            byte[] plain = validateText(text);
            String receiverPublicKey = publicKeyPayloadService.resolvePublicKey(receiverPublicKeyToken);
            SecretKey aesKey = cryptoSupport.generateAESKey();
            byte[] encryptedSessionKey = Base64.getDecoder().decode(cryptoSupport.encryptAESKeyForReceiver(aesKey, receiverPublicKey));
            byte[] nonce = randomBytes(GCM_NONCE_BYTES);
            byte[] aad = fstTextAad(encryptedSessionKey, nonce, plain.length);
            AesGcmChunk encrypted = cryptoSupport.encryptChunk(plain, aesKey, nonce, aad);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("version", (int) FST2_VERSION);
            payload.put("keyWrapAlg", (int) ALG_KEY_WRAP_RSA);
            payload.put("contentAlg", (int) ALG_AES_256_GCM);
            payload.put("encryptedSessionKey", encryptedSessionKey);
            payload.put("nonce", nonce);
            payload.put("ciphertext", encrypted.ciphertext());
            payload.put("tag", encrypted.tag());
            payload.put("plaintextLength", plain.length);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(CborLite.encodeCanonical(payload));
            return new FstTextEncryptResult(true, FST_TEXT_PREFIX + encoded, plain.length);
        }
        catch(Exception ex)
        {
            throw new IllegalStateException("FST-TEXT1 encryption failed: "+ex.getMessage(), ex);
        }
    }

    public FstTextDecryptResult decryptText(String payloadText)
    {
        try
        {
            String encoded = normalizeFstTextPayload(payloadText);
            byte[] payloadBytes = decodeBase64Url(encoded);
            Map<String, Object> payload = CborLite.decodeMap(payloadBytes);
            int version = intValue(payload, "version");
            int keyWrapAlg = intValue(payload, "keyWrapAlg");
            int contentAlg = intValue(payload, "contentAlg");
            if(version != FST2_VERSION || keyWrapAlg != ALG_KEY_WRAP_RSA || contentAlg != ALG_AES_256_GCM)
            {
                throw new IllegalArgumentException("Unsupported FST-TEXT1 version or algorithm");
            }
            byte[] encryptedSessionKey = bytesValue(payload, "encryptedSessionKey");
            byte[] nonce = bytesValue(payload, "nonce");
            byte[] ciphertext = bytesValue(payload, "ciphertext");
            byte[] tag = bytesValue(payload, "tag");
            int plaintextLength = intValue(payload, "plaintextLength");
            if(nonce.length != GCM_NONCE_BYTES)
            {
                throw new IllegalArgumentException("Invalid FST-TEXT1 nonce length");
            }
            if(tag.length != GCM_TAG_BYTES)
            {
                throw new IllegalArgumentException("Invalid FST-TEXT1 tag length");
            }
            if(plaintextLength <= 0 || plaintextLength > MAX_TEXT_BYTES)
            {
                throw new IllegalArgumentException("FST-TEXT1 plaintext length exceeds limit");
            }
            SecretKey aesKey = decryptSessionKey(encryptedSessionKey);
            byte[] aad = fstTextAad(encryptedSessionKey, nonce, plaintextLength);
            byte[] plain = cryptoSupport.decryptChunk(nonce, ciphertext, tag, aesKey, aad);
            if(plain.length != plaintextLength)
            {
                throw new IllegalArgumentException("FST-TEXT1 plaintext length mismatch");
            }
            String text = decodeUtf8Strict(plain);
            return new FstTextDecryptResult(true, text, plaintextLength);
        }
        catch(Exception ex)
        {
            Throwable cause = ex;
            while(cause != null)
            {
                if(cause instanceof BadPaddingException)
                {
                    throw new IllegalStateException(
                            "Decryption failed: The local private key does not match the public key "
                                    + "used for encryption (RSA-OAEP padding validation failed).\n\n"
                                    + "Common causes:\n"
                                    + "1. You recently reinstalled the software, which generated a new key pair, "
                                    + "but the sender is still encrypting with your old public key.\n"
                                    + "2. You imported an incorrect private key.\n\n"
                                    + "Solutions:\n"
                                    + "- Ask the sender to obtain your new public key (export from app settings) "
                                    + "and re-encrypt the content.\n"
                                    + "- If you have a backup of your old private key, restore it via 'Import Private Key'.",
                            ex);
                }
                cause = cause.getCause();
            }
            throw new IllegalStateException("FST-TEXT1 decryption failed: "+ex.getMessage(), ex);
        }
    }

    private void writeEncryptedBlocks(Path source, DataOutputStream out, SecretKey aesKey, byte[] nonceSeed, int chunkSize) throws IOException, GeneralSecurityException
    {
        try(InputStream input = Files.newInputStream(source))
        {
            byte[] buffer = new byte[chunkSize];
            int blockIndex = 0;
            int length;
            while((length = input.read(buffer)) != -1)
            {
                byte[] plain = Arrays.copyOf(buffer, length);
                byte[] aad = fst2BlockAad(blockIndex, length, length);
                AesGcmChunk encrypted = cryptoSupport.encryptChunk(
                        plain,
                        aesKey,
                        deriveNonce(nonceSeed, "FST2-block", blockIndex),
                        aad
                );
                out.writeInt(blockIndex);
                out.writeInt(length);
                out.writeInt(encrypted.ciphertext().length);
                out.write(encrypted.ciphertext());
                writeLengthBytes(out, encrypted.tag());
                blockIndex++;
            }
        }
    }

    private byte[] validateText(String text)
    {
        if(text == null || text.trim().isEmpty())
        {
            throw new IllegalArgumentException("Text cannot be empty");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length > MAX_TEXT_BYTES)
        {
            throw new IllegalArgumentException("Text exceeds 16 KiB");
        }
        return bytes;
    }

    private String normalizeFstTextPayload(String payload)
    {
        String trimmed = payload == null ? "" : payload.trim();
        if(!trimmed.startsWith(FST_TEXT_PREFIX))
        {
            throw new IllegalArgumentException("Not an FST-TEXT1 payload");
        }
        String encoded = trimmed.substring(FST_TEXT_PREFIX.length());
        StringBuilder builder = new StringBuilder(encoded.length());
        for(int i = 0; i < encoded.length(); i++)
        {
            if(!Character.isWhitespace(encoded.charAt(i)))
            {
                builder.append(encoded.charAt(i));
            }
        }
        return builder.toString();
    }

    private byte[] decodeBase64Url(String encoded)
    {
        String normalized = encoded;
        int remainder = normalized.length() % 4;
        if(remainder != 0)
        {
            normalized = normalized + "=".repeat(4 - remainder);
        }
        return Base64.getUrlDecoder().decode(normalized);
    }

    private String decodeUtf8Strict(byte[] plain)
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plain)).toString();
        }
        catch(CharacterCodingException ex)
        {
            throw new IllegalArgumentException("FST-TEXT1 plaintext is not valid UTF-8", ex);
        }
    }

    private SecretKey decryptSessionKey(byte[] encryptedSessionKey) throws GeneralSecurityException
    {
        return cryptoSupport.decryptAESKey(Base64.getEncoder().encodeToString(encryptedSessionKey));
    }

    private Path resolveOutputDir(Path outputDir)
    {
        if(outputDir != null)
        {
            return outputDir.toAbsolutePath().normalize();
        }
        return PathInputNormalizer.toPath(transferProperties.getReceiveDir()).toAbsolutePath().normalize();
    }

    private int computeTotalBlocks(long fileSize, int chunkSize)
    {
        if(chunkSize <= 0)
        {
            throw new IllegalArgumentException("transfer.chunk-size-bytes must be greater than 0");
        }
        if(fileSize == 0)
        {
            return 0;
        }
        long totalBlocks = (fileSize + chunkSize - 1L) / chunkSize;
        if(totalBlocks > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("File is too large for FST2: block count exceeds supported limit");
        }
        return (int) totalBlocks;
    }

    private void validateHeaderValues(long fileSize, int chunkSize, int totalBlocks)
    {
        if(fileSize < 0)
        {
            throw new IllegalArgumentException("Invalid FST2 file size");
        }
        int expectedTotalBlocks = computeTotalBlocks(fileSize, chunkSize);
        if(totalBlocks != expectedTotalBlocks)
        {
            throw new IllegalArgumentException("Invalid FST2 total block count");
        }
    }

    private void validatePlaintextLength(long fileSize, int chunkSize, int totalBlocks, int blockIndex, int plaintextLength)
    {
        if(plaintextLength < 0 || plaintextLength > chunkSize)
        {
            throw new IllegalArgumentException("Invalid FST2 block plaintext length");
        }
        if(totalBlocks == 0)
        {
            return;
        }
        int expectedLength = blockIndex == totalBlocks - 1
                ? (int) (fileSize - (long) blockIndex * chunkSize)
                : chunkSize;
        if(plaintextLength != expectedLength)
        {
            throw new IllegalArgumentException("FST2 block plaintext length does not match file size");
        }
    }

    private void writeFst2Prefix(DataOutputStream out) throws IOException
    {
        out.write(FST2_MAGIC);
        out.writeByte(FST2_VERSION);
        out.writeByte(ALG_KEY_WRAP_RSA);
        out.writeByte(ALG_AES_256_GCM);
        out.writeByte(ALG_NONCE_HMAC_SHA256);
    }

    private void readAndValidateFst2Prefix(DataInputStream in) throws IOException
    {
        byte[] magic = in.readNBytes(FST2_MAGIC.length);
        if(!Arrays.equals(magic, FST2_MAGIC))
        {
            throw new IllegalArgumentException("Invalid magic; not an FST2 file");
        }
        byte version = in.readByte();
        byte keyWrapAlg = in.readByte();
        byte contentAlg = in.readByte();
        byte nonceDerivationAlg = in.readByte();
        if(version != FST2_VERSION || keyWrapAlg != ALG_KEY_WRAP_RSA || contentAlg != ALG_AES_256_GCM || nonceDerivationAlg != ALG_NONCE_HMAC_SHA256)
        {
            throw new IllegalArgumentException("Unsupported FST2 version or algorithm");
        }
    }

    private void writeLengthBytes(DataOutputStream out, byte[] bytes) throws IOException
    {
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private byte[] readLengthBytes(DataInputStream in, String fieldName) throws IOException
    {
        int length = readNonNegativeLength(in, fieldName);
        byte[] bytes = in.readNBytes(length);
        if(bytes.length != length)
        {
            throw new EOFException("FST2 field is truncated: "+fieldName);
        }
        return bytes;
    }

    private int readNonNegativeLength(DataInputStream in, String fieldName) throws IOException
    {
        int length = in.readInt();
        if(length < 0)
        {
            throw new IllegalArgumentException("Negative FST2 length: "+fieldName);
        }
        return length;
    }

    private byte[] fst2HeaderAad(byte[] encryptedSessionKey, byte[] nonceSeed, int headerCiphertextLength) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(DataOutputStream out = new DataOutputStream(bytes))
        {
            writeFst2Prefix(out);
            writeLengthBytes(out, encryptedSessionKey);
            writeLengthBytes(out, nonceSeed);
            out.writeInt(headerCiphertextLength);
        }
        return bytes.toByteArray();
    }

    private byte[] fst2BlockAad(int blockIndex, int plaintextLength, int ciphertextLength) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try(DataOutputStream out = new DataOutputStream(bytes))
        {
            writeFst2Prefix(out);
            out.writeInt(blockIndex);
            out.writeInt(plaintextLength);
            out.writeInt(ciphertextLength);
        }
        return bytes.toByteArray();
    }

    private byte[] fstTextAad(byte[] encryptedSessionKey, byte[] nonce, int plaintextLength)
    {
        Map<String, Object> aad = new LinkedHashMap<>();
        aad.put("protocol", FST_TEXT_PREFIX);
        aad.put("version", (int) FST2_VERSION);
        aad.put("keyWrapAlg", (int) ALG_KEY_WRAP_RSA);
        aad.put("contentAlg", (int) ALG_AES_256_GCM);
        aad.put("encryptedSessionKey", encryptedSessionKey);
        aad.put("nonce", nonce);
        aad.put("plaintextLength", plaintextLength);
        return CborLite.encodeCanonical(aad);
    }

    private byte[] deriveNonce(byte[] seed, String domain, int index) throws GeneralSecurityException
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(seed, "HmacSHA256"));
        mac.update(domain.getBytes(StandardCharsets.US_ASCII));
        mac.update((byte) 0);
        mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(index).array());
        return Arrays.copyOf(mac.doFinal(), GCM_NONCE_BYTES);
    }

    private byte[] randomBytes(int length)
    {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String sanitizeFileName(String fileName)
    {
        String baseName;
        try
        {
            Path name = Path.of(fileName).getFileName();
            baseName = name == null ? "" : name.toString();
        }
        catch(Exception ex)
        {
            baseName = fileName;
        }
        StringBuilder sanitized = new StringBuilder(baseName.length());
        for(int i = 0; i < baseName.length(); i++)
        {
            char ch = baseName.charAt(i);
            if(ch == '/' || ch == '\\' || Character.isISOControl(ch))
            {
                sanitized.append('_');
            }
            else
            {
                sanitized.append(ch);
            }
        }
        String result = sanitized.toString().trim();
        return result.isEmpty() ? "decrypted-file" : result;
    }

    private Path uniqueOutputPath(Path dir, String fileName)
    {
        Path candidate = dir.resolve(fileName);
        if(Files.notExists(candidate))
        {
            return candidate;
        }
        int dotIndex = fileName.lastIndexOf('.');
        String stem = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
        for(int i = 1; i < Integer.MAX_VALUE; i++)
        {
            candidate = dir.resolve(stem + " (" + i + ")" + extension);
            if(Files.notExists(candidate))
            {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to find available output filename");
    }

    private void moveCompletedTempFile(Path tempPath, Path finalPath) throws IOException
    {
        try
        {
            Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
        }
        catch(AtomicMoveNotSupportedException ex)
        {
            Files.move(tempPath, finalPath);
        }
    }

    private String stringValue(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(value instanceof String string)
        {
            return string;
        }
        throw new IllegalArgumentException("FST2 header field must be string: "+key);
    }

    private long longValue(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(value instanceof Number number)
        {
            return number.longValue();
        }
        throw new IllegalArgumentException("field must be integer: "+key);
    }

    private int intValue(Map<String, Object> map, String key)
    {
        long value = longValue(map, key);
        if(value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
        {
            throw new IllegalArgumentException("field is outside int range: "+key);
        }
        return (int) value;
    }

    private byte[] bytesValue(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(value instanceof byte[] bytes)
        {
            return bytes;
        }
        throw new IllegalArgumentException("field must be bytes: "+key);
    }

    public record Fst2EncryptResult(boolean success, Path outputPath, String fileName, long fileSize, int totalBlocks) {}
    public record Fst2DecryptResult(boolean success, Path outputPath, String fileName, long fileSize, int totalBlocks) {}
    public record FstTextEncryptResult(boolean success, String payload, int plaintextLength) {}
    public record FstTextDecryptResult(boolean success, String text, int plaintextLength) {}
}
