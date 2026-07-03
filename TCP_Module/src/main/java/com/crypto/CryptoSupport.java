package com.crypto;

import com.common.config.CryptoServiceProperties;
import com.common.crypto.AesGcmChunk;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Author: LQH
 * Date: 2026-04-26
 * Update: 2026-07-03 — 全部转为 Java 本地实现; 私钥文件 PBKDF2+AES-256-GCM 加密存储
 * Purpose: 提供一组混合加密相关的组件 — RSA、签名、AES-GCM、密钥管理、密钥文件安全存储
 *
 * 所有密码学操作均由 Java 标准库本地实现，不再依赖外部 Python crypto service。
 * RSA-2048 / RSA-PSS(SHA-256) / RSA-OAEP(SHA-256) / AES-256-GCM / SHA-256 fingerprint /
 * PBKDF2WithHmacSHA256 私钥加密存储
 */

@Component
@Slf4j
public class CryptoSupport
{
    // ── 算法常量 ──────────────────────────────────────────────
    private static final int RSA_KEY_SIZE = 2048;
    private static final int AES_KEY_BITS = 256;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;
    private static final int GCM_TAG_BITS = 128;

    // ── 文件权限常量 ──────────────────────────────────────────
    /** 私钥：仅 owner 可读写 */
    private static final Set<PosixFilePermission> PRIV_KEY_PERMS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    /** 公钥：owner 读写，其他用户只读 */
    private static final Set<PosixFilePermission> PUB_KEY_PERMS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ);
    /** 密钥目录：仅 owner 可读写访问 */
    private static final Set<PosixFilePermission> KEY_DIR_PERMS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final String PEM_PRIV_PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIV_PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PEM_PRIV_PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";
    private static final String PEM_PRIV_PKCS1_FOOTER = "-----END RSA PRIVATE KEY-----";
    private static final String PEM_PUB_HEADER = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_PUB_FOOTER = "-----END PUBLIC KEY-----";

    // ── 加密私钥存储 — PBKDF2 + AES-256-GCM ─────────────────────
    private static final String PEM_ENC_PRIV_HEADER = "-----BEGIN FST ENCRYPTED PRIVATE KEY-----";
    private static final String PEM_ENC_PRIV_FOOTER = "-----END FST ENCRYPTED PRIVATE KEY-----";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_SALT_BYTES = 32;
    private static final int STORAGE_NONCE_BYTES = 12;
    private static final int STORAGE_TAG_BYTES = 16;
    /**
     * 用于派生存储密钥的应用内嵌 secret。
     * 可通过环境变量 FST_CRYPTO_SECRET 覆盖（桌面打包时建议注入随机值）。
     */
    private static final String APP_SECRET = System.getenv().getOrDefault(
            "FST_CRYPTO_SECRET",
            "FST-2026-LQH-default-secret-material-v1");

    // ── 依赖与状态 ────────────────────────────────────────────
    private final CryptoServiceProperties cryptoServiceProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    private Path keyDir;
    private Path privateKeyFile;
    private Path publicKeyFile;

    // 兼容旧调用保留的字段
    private String cryptoServiceUrl;

    public CryptoSupport(CryptoServiceProperties cryptoServiceProperties)
    {
        this.cryptoServiceProperties = cryptoServiceProperties;
    }

    // ── 初始化 ──────────────────────────────────────────────────

    @PostConstruct
    public synchronized void init()
    {
        String dir = cryptoServiceProperties.getKeyDir();
        if(dir == null || dir.isBlank())
        {
            dir = System.getProperty("user.home") + "/.file-security-transmission/crypto-keys";
        }
        this.keyDir = Path.of(dir).toAbsolutePath().normalize();
        this.privateKeyFile = keyDir.resolve("private_key.pem");
        this.publicKeyFile = keyDir.resolve("public_key.pem");
        this.cryptoServiceUrl = "local-java";

        try
        {
            Files.createDirectories(keyDir);
            protectKeyDir(keyDir);
            protectExistingKeyFiles();
            migrateUnencryptedKeys();
        }
        catch(IOException e)
        {
            log.warn("Failed to create key directory: {}", keyDir, e);
        }
        log.info("CryptoSupport initialized — keyDir={} privateKeyFile={} publicKeyFile={}",
                keyDir, privateKeyFile, publicKeyFile);
    }

    // ── 兼容旧 API ─────────────────────────────────────────────

    public void setCRYPTO_SERVICE_URL(String url)
    {
        this.cryptoServiceUrl = url;
    }

    public String getCryptoServiceUrl()
    {
        return cryptoServiceUrl;
    }

    // ═══════════════════════════════════════════════════════════
    //  密钥查询
    // ═══════════════════════════════════════════════════════════

    public String getEncodedPublicKey()
    {
        return readTextFile(ensurePublicKeyFile());
    }

    public String getEncodedPrivateKey()
    {
        if(Files.notExists(privateKeyFile))
        {
            throw new IllegalStateException("Private key not found: " + privateKeyFile);
        }
        return readTextFile(privateKeyFile);
    }

    public synchronized Map<String, Object> keyStatus()
    {
        boolean hasPrivate = Files.exists(privateKeyFile);
        return Map.of(
                "hasPrivateKey", String.valueOf(hasPrivate),
                "hasPublicKey", String.valueOf(hasPrivate || Files.exists(publicKeyFile))
        );
    }

    // ═══════════════════════════════════════════════════════════
    //  签名 / 验签 — RSA-PSS (SHA-256)
    // ═══════════════════════════════════════════════════════════

    public String signToBase64(String challenge) throws GeneralSecurityException
    {
        PrivateKey privateKey = loadPrivateKey();
        Signature sig = Signature.getInstance("RSASSA-PSS");
        sig.setParameter(new PSSParameterSpec("SHA-256", "MGF1",
                MGF1ParameterSpec.SHA256, 32, 1));
        sig.initSign(privateKey);
        sig.update(challenge.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    public boolean verifySignature(String publicKeyBase64, String challenge, String signatureBase64)
    {
        try
        {
            PublicKey publicKey = parsePublicKeyFromPem(publicKeyBase64);
            Signature sig = Signature.getInstance("RSASSA-PSS");
            sig.setParameter(new PSSParameterSpec("SHA-256", "MGF1",
                    MGF1ParameterSpec.SHA256, 32, 1));
            sig.initVerify(publicKey);
            sig.update(challenge.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        }
        catch(Exception e)
        {
            log.debug("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  AES 密钥生成
    // ═══════════════════════════════════════════════════════════

    public SecretKey generateAESKey() throws GeneralSecurityException
    {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_BITS, secureRandom);
        return keyGenerator.generateKey();
    }

    // ═══════════════════════════════════════════════════════════
    //  RSA 加解密 — OAEP (SHA-256)
    // ═══════════════════════════════════════════════════════════

    public String encryptAESKeyForReceiver(SecretKey aesKey, String receiverPublicKeyBase64)
            throws GeneralSecurityException
    {
        PublicKey publicKey = parsePublicKeyFromPem(receiverPublicKeyBase64);
        byte[] cipher = rsaOaepEncrypt(aesKey.getEncoded(), publicKey);
        return Base64.getEncoder().encodeToString(cipher);
    }

    public SecretKey decryptAESKey(String encryptedAESKeyBase64) throws GeneralSecurityException
    {
        PrivateKey privateKey = loadPrivateKey();
        byte[] plain = rsaOaepDecrypt(Base64.getDecoder().decode(encryptedAESKeyBase64), privateKey);
        return new SecretKeySpec(plain, "AES");
    }

    public String encryptWithPublicKeyToBase64(byte[] plain, String publicKeyBase64)
            throws GeneralSecurityException
    {
        PublicKey publicKey = parsePublicKeyFromPem(publicKeyBase64);
        return Base64.getEncoder().encodeToString(rsaOaepEncrypt(plain, publicKey));
    }

    public byte[] decryptWithPrivateKey(String encryptedBase64) throws GeneralSecurityException
    {
        PrivateKey privateKey = loadPrivateKey();
        return rsaOaepDecrypt(Base64.getDecoder().decode(encryptedBase64), privateKey);
    }

    // ── RSA-OAEP 底层操作 ─────────────────────────────────────

    private byte[] rsaOaepEncrypt(byte[] plain, PublicKey publicKey) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plain);
    }

    private byte[] rsaOaepDecrypt(byte[] ciphertext, PrivateKey privateKey) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(ciphertext);
    }

    // ═══════════════════════════════════════════════════════════
    //  AES-GCM 块加解密（本地实现，保留原逻辑）
    // ═══════════════════════════════════════════════════════════

    public AesGcmChunk encryptChunk(byte[] plain, SecretKey aesKey) throws GeneralSecurityException
    {
        validateAes256Key(aesKey);

        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));

        byte[] encrypted = cipher.doFinal(plain);
        int ciphertextLength = encrypted.length - GCM_TAG_BYTES;
        if(ciphertextLength < 0)
        {
            throw new GeneralSecurityException("AES-GCM output shorter than tag length");
        }

        byte[] ciphertext = Arrays.copyOfRange(encrypted, 0, ciphertextLength);
        byte[] tag = Arrays.copyOfRange(encrypted, ciphertextLength, encrypted.length);
        return new AesGcmChunk(nonce, ciphertext, tag);
    }

    public AesGcmChunk encryptChunk(byte[] plain, SecretKey aesKey, byte[] nonce, byte[] aad)
            throws GeneralSecurityException
    {
        validateAes256Key(aesKey);
        if(nonce.length != GCM_NONCE_BYTES)
        {
            throw new GeneralSecurityException("AES-GCM nonce must be " + GCM_NONCE_BYTES + " bytes");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if(aad != null && aad.length > 0)
        {
            cipher.updateAAD(aad);
        }

        byte[] encrypted = cipher.doFinal(plain);
        int ciphertextLength = encrypted.length - GCM_TAG_BYTES;
        if(ciphertextLength < 0)
        {
            throw new GeneralSecurityException("AES-GCM output shorter than tag length");
        }

        byte[] ciphertext = Arrays.copyOfRange(encrypted, 0, ciphertextLength);
        byte[] tag = Arrays.copyOfRange(encrypted, ciphertextLength, encrypted.length);
        return new AesGcmChunk(nonce, ciphertext, tag);
    }

    public byte[] decryptChunk(byte[] nonce, byte[] ciphertext, byte[] tag, SecretKey aesKey)
            throws GeneralSecurityException
    {
        validateAes256Key(aesKey);
        if(nonce.length != GCM_NONCE_BYTES)
        {
            throw new GeneralSecurityException("AES-GCM nonce must be " + GCM_NONCE_BYTES + " bytes");
        }
        if(tag.length != GCM_TAG_BYTES)
        {
            throw new GeneralSecurityException("AES-GCM tag must be " + GCM_TAG_BYTES + " bytes");
        }

        byte[] encrypted = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.length);
        System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        return cipher.doFinal(encrypted);
    }

    public byte[] decryptChunk(byte[] nonce, byte[] ciphertext, byte[] tag, SecretKey aesKey, byte[] aad)
            throws GeneralSecurityException
    {
        validateAes256Key(aesKey);
        if(nonce.length != GCM_NONCE_BYTES)
        {
            throw new GeneralSecurityException("AES-GCM nonce must be " + GCM_NONCE_BYTES + " bytes");
        }
        if(tag.length != GCM_TAG_BYTES)
        {
            throw new GeneralSecurityException("AES-GCM tag must be " + GCM_TAG_BYTES + " bytes");
        }

        byte[] encrypted = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.length);
        System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if(aad != null && aad.length > 0)
        {
            cipher.updateAAD(aad);
        }
        return cipher.doFinal(encrypted);
    }

    // ═══════════════════════════════════════════════════════════
    //  公钥指纹 — SHA-256
    // ═══════════════════════════════════════════════════════════

    public synchronized String publicKeyFingerprint() throws GeneralSecurityException
    {
        return publicKeyFingerprint(getEncodedPublicKey());
    }

    public String publicKeyFingerprint(String publicKeyBase64) throws GeneralSecurityException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String pem = publicKeyBase64.contains("-----BEGIN")
                ? publicKeyBase64
                : KeyArtifactUtil.toPemPublicKey(publicKeyBase64);
        md.update(pem.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for(byte b : digest)
        {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    // ═══════════════════════════════════════════════════════════
    //  密钥生命周期管理
    // ═══════════════════════════════════════════════════════════

    public synchronized Map<String, String> generateKeyPair()
    {
        if(Files.exists(privateKeyFile) || Files.exists(publicKeyFile))
        {
            throw new IllegalStateException("Key pair already exists");
        }
        try
        {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(RSA_KEY_SIZE, secureRandom);
            KeyPair keyPair = gen.generateKeyPair();

            String privatePem = toPemPrivateKey(keyPair.getPrivate());
            String publicPem = toPemPublicKey(keyPair.getPublic());

            writePrivateKeyFile(privateKeyFile, privatePem);
            writePublicKeyFile(publicKeyFile, publicPem);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("success", "true");
            result.put("privateKey", privatePem);
            result.put("publicKey", publicPem);
            return result;
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalStateException("Key generation failed", e);
        }
    }

    public synchronized Map<String, String> deleteKeyPair()
    {
        boolean deletedPrivate = deleteFileIfExists(privateKeyFile);
        boolean deletedPublic = deleteFileIfExists(publicKeyFile);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("success", "true");
        result.put("deletedPrivateKey", String.valueOf(deletedPrivate));
        result.put("deletedPublicKey", String.valueOf(deletedPublic));
        return result;
    }

    public synchronized Map<String, String> deleteLocalKeyPair()
    {
        return deleteKeyPair();
    }

    public synchronized void importPrivateKeyText(String privateKeyText)
    {
        byte[] pemBytes = normalizePrivateKeyInput(privateKeyText);
        importPrivateKeyAndPersist(pemBytes);
    }

    public synchronized void importPrivateKeyFile(Path privateKeyPath)
    {
        try
        {
            byte[] pemBytes = Files.readAllBytes(privateKeyPath);
            importPrivateKeyAndPersist(pemBytes);
        }
        catch(IOException e)
        {
            throw new IllegalStateException("Failed to read private key file: " + privateKeyPath, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  密钥 I/O 内部方法
    // ═══════════════════════════════════════════════════════════

    private PrivateKey loadPrivateKey()
    {
        if(Files.notExists(privateKeyFile))
        {
            throw new IllegalStateException("Private key not found: " + privateKeyFile);
        }
        try
        {
            String pem = Files.readString(privateKeyFile);
            return parsePrivateKeyFromPem(pem);
        }
        catch(IOException e)
        {
            throw new IllegalStateException("Failed to read private key: " + privateKeyFile, e);
        }
    }

    private Path ensurePublicKeyFile()
    {
        if(Files.exists(publicKeyFile))
        {
            return publicKeyFile;
        }
        // 从私钥推导公钥
        PrivateKey privateKey = loadPrivateKey();
        try
        {
            // 尝试从私钥文件读取; 如果它是 RSA, 用 KeyFactory 推导
            String privPem = Files.readString(privateKeyFile);
            PrivateKey pk = parsePrivateKeyFromPem(privPem);
            if(pk instanceof java.security.interfaces.RSAPrivateKey rsaKey)
            {
                java.security.spec.RSAPublicKeySpec pubSpec = new java.security.spec.RSAPublicKeySpec(
                        rsaKey.getModulus(),
                        ((java.security.interfaces.RSAPrivateCrtKey) rsaKey).getPublicExponent()
                );
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(pubSpec);
                String pubPem = toPemPublicKey(pubKey);
                writePublicKeyFile(publicKeyFile, pubPem);
                log.info("Repaired missing public key file from private key: {}", publicKeyFile);
            }
            return publicKeyFile;
        }
        catch(Exception e)
        {
            throw new IllegalStateException("Failed to derive public key from private key", e);
        }
    }

    private void importPrivateKeyAndPersist(byte[] pemBytes)
    {
        try
        {
            String pemText = new String(pemBytes, StandardCharsets.UTF_8);
            PrivateKey privateKey = parsePrivateKeyFromPem(pemText);

            // 规范化为 PKCS#8 PEM
            String normalizedPem = toPemPrivateKey(privateKey);

            // 推导公钥
            String publicPem;
            if(privateKey instanceof java.security.interfaces.RSAPrivateCrtKey rsaKey)
            {
                RSAPublicKeySpec pubSpec = new RSAPublicKeySpec(
                        rsaKey.getModulus(),
                        rsaKey.getPublicExponent()
                );
                KeyFactory kf = KeyFactory.getInstance("RSA");
                PublicKey pubKey = kf.generatePublic(pubSpec);
                publicPem = toPemPublicKey(pubKey);
            }
            else
            {
                throw new GeneralSecurityException("Unsupported private key type: " +
                        privateKey.getClass().getName());
            }

            Files.createDirectories(keyDir);
            writePrivateKeyFile(privateKeyFile, normalizedPem);
            writePublicKeyFile(publicKeyFile, publicPem);

            log.info("Imported private key and derived public key");
        }
        catch(GeneralSecurityException | IOException e)
        {
            throw new IllegalStateException("Failed to import private key", e);
        }
    }

    private byte[] normalizePrivateKeyInput(String text)
    {
        if(text == null || text.isBlank())
        {
            throw new IllegalArgumentException("privateKey is required");
        }
        String normalized = text.strip()
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n");
        if(normalized.contains("-----BEGIN") && normalized.contains("PRIVATE KEY-----"))
        {
            return (normalized.stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8);
        }
        // 尝试 Base64 解码
        try
        {
            byte[] decoded = Base64.getDecoder().decode(normalized);
            String decodedText = new String(decoded, StandardCharsets.UTF_8);
            if(!decodedText.contains("-----BEGIN") || !decodedText.contains("PRIVATE KEY-----"))
            {
                throw new IllegalArgumentException("Decoded privateKey is not a PEM private key");
            }
            return (decodedText.stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8);
        }
        catch(IllegalArgumentException e)
        {
            throw new IllegalArgumentException("privateKey must be PEM text or Base64-encoded PEM", e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PEM 解析 / 生成
    // ═══════════════════════════════════════════════════════════

    private PrivateKey parsePrivateKeyFromPem(String pem)
    {
        try
        {
            String trimmed = pem.strip();
            // FST 加密私钥 (PBKDF2 + AES-256-GCM)
            if(trimmed.contains(PEM_ENC_PRIV_HEADER))
            {
                byte[] payload = extractPemBody(trimmed, PEM_ENC_PRIV_HEADER, PEM_ENC_PRIV_FOOTER);
                byte[] der = decryptFromStorage(payload);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(spec);
            }
            if(trimmed.contains(PEM_PRIV_PKCS8_HEADER))
            {
                byte[] der = extractPemBody(trimmed, PEM_PRIV_PKCS8_HEADER, PEM_PRIV_PKCS8_FOOTER);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(spec);
            }
            if(trimmed.contains(PEM_PRIV_PKCS1_HEADER))
            {
                byte[] der = extractPemBody(trimmed, PEM_PRIV_PKCS1_HEADER, PEM_PRIV_PKCS1_FOOTER);
                return parsePkcs1PrivateKey(der);
            }
            // 尝试作为纯 Base64 (PKCS#8 DER)
            byte[] der = Base64.getDecoder().decode(trimmed.replaceAll("\\s", ""));
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalArgumentException("Failed to parse private key PEM", e);
        }
    }

    private PublicKey parsePublicKeyFromPem(String pem)
    {
        try
        {
            String trimmed = pem.strip();
            if(trimmed.contains(PEM_PUB_HEADER))
            {
                byte[] der = extractPemBody(trimmed, PEM_PUB_HEADER, PEM_PUB_FOOTER);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);
            }
            // 尝试标准 Base64 → 构造 PEM
            String base64 = KeyArtifactUtil.isBareBase64PublicKey(trimmed)
                    ? trimmed
                    : trimmed.replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalArgumentException("Failed to parse public key PEM", e);
        }
    }

    /**
     * 解析 PKCS#1 RSAPrivateKey DER 结构 (TraditionalOpenSSL 格式)
     *
     * RSAPrivateKey ::= SEQUENCE {
     *   version           INTEGER (0),
     *   modulus           INTEGER,  -- n
     *   publicExponent    INTEGER,  -- e
     *   privateExponent   INTEGER,  -- d
     *   prime1            INTEGER,  -- p
     *   prime2            INTEGER,  -- q
     *   exponent1         INTEGER,  -- d mod (p-1)
     *   exponent2         INTEGER,  -- d mod (q-1)
     *   coefficient       INTEGER   -- (inverse of q) mod p
     * }
     */
    private PrivateKey parsePkcs1PrivateKey(byte[] der) throws GeneralSecurityException
    {
        DerReader reader = new DerReader(der);
        reader.expectTag(0x30); // SEQUENCE
        int seqLen = reader.readLength();
        int endPos = reader.position() + seqLen;

        reader.expectTag(0x02); // INTEGER version
        int versionLen = reader.readLength();
        reader.skip(versionLen);

        BigInteger n = reader.readInteger();
        BigInteger e = reader.readInteger();
        BigInteger d = reader.readInteger();
        BigInteger p = reader.readInteger();
        BigInteger q = reader.readInteger();
        BigInteger dp = reader.readInteger();
        BigInteger dq = reader.readInteger();
        BigInteger qinv = reader.readInteger();

        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(n, e, d, p, q, dp, dq, qinv);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private byte[] extractPemBody(String pem, String header, String footer)
    {
        int start = pem.indexOf(header);
        int end = pem.indexOf(footer);
        if(start < 0 || end < 0 || end <= start)
        {
            throw new IllegalArgumentException("Invalid PEM format: missing " + header + " / " + footer);
        }
        String body = pem.substring(start + header.length(), end);
        return Base64.getMimeDecoder().decode(body);
    }

    /**
     * 将私钥编码为加密 PEM。使用 PBKDF2 + AES-256-GCM 加密 PKCS#8 DER，
     * 只有本机本用户本应用才能解开。
     */
    private String toPemPrivateKey(PrivateKey privateKey)
    {
        try
        {
            byte[] der = privateKey.getEncoded(); // PKCS#8
            byte[] encryptedPayload = encryptForStorage(der);
            return pemEncode(PEM_ENC_PRIV_HEADER, PEM_ENC_PRIV_FOOTER, encryptedPayload);
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalStateException("Failed to encrypt private key for storage", e);
        }
    }

    private String toPemPublicKey(PublicKey publicKey)
    {
        byte[] der = publicKey.getEncoded(); // X.509
        return pemEncode(PEM_PUB_HEADER, PEM_PUB_FOOTER, der);
    }

    private String pemEncode(String header, String footer, byte[] der)
    {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(der);
        return header + "\n" + base64 + "\n" + footer + "\n";
    }

    // ═══════════════════════════════════════════════════════════
    //  私钥加密存储 — PBKDF2 + AES-256-GCM
    // ═══════════════════════════════════════════════════════════

    /**
     * 从机器身份 + 应用 secret 派生 AES-256 密钥。
     * 材料 = user.name + user.home + APP_SECRET。
     * 换用户/换机器 → 派生密钥不同 → 解密失败。
     */
    private SecretKey deriveStorageKey(byte[] salt) throws GeneralSecurityException
    {
        String material = System.getProperty("user.name", "")
                + "|" + System.getProperty("user.home", "")
                + "|" + APP_SECRET;
        PBEKeySpec pbeSpec = new PBEKeySpec(material.toCharArray(), salt,
                PBKDF2_ITERATIONS, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] derived = factory.generateSecret(pbeSpec).getEncoded();
        return new SecretKeySpec(derived, "AES");
    }

    /**
     * 加密 PKCS#8 DER 为存储格式:
     *   salt(32) || iterations(4) || nonce(12) || ct_len(4) || ciphertext || tag(16)
     */
    private byte[] encryptForStorage(byte[] pkcs8Der) throws GeneralSecurityException
    {
        byte[] salt = new byte[PBKDF2_SALT_BYTES];
        secureRandom.nextBytes(salt);
        SecretKey storageKey = deriveStorageKey(salt);

        byte[] nonce = new byte[STORAGE_NONCE_BYTES];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, storageKey,
                new GCMParameterSpec(GCM_TAG_BITS, nonce));
        byte[] encrypted = cipher.doFinal(pkcs8Der);

        int ctLen = encrypted.length - STORAGE_TAG_BYTES;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try(DataOutputStream dos = new DataOutputStream(buf))
        {
            dos.write(salt);
            dos.writeInt(PBKDF2_ITERATIONS);
            dos.write(nonce);
            dos.writeInt(ctLen);
            dos.write(encrypted, 0, ctLen);
            dos.write(encrypted, ctLen, STORAGE_TAG_BYTES);
        }
        catch(IOException e)
        {
            throw new GeneralSecurityException("Failed to encode encrypted key payload", e);
        }
        log.debug("Private key encrypted for storage (PBKDF2 iter={}, AES-256-GCM)", PBKDF2_ITERATIONS);
        return buf.toByteArray();
    }

    /**
     * 解密存储格式，恢复 PKCS#8 DER。
     * 如果密钥派生失败（换了机器/用户），返回明确的错误信息而非静默失败。
     */
    private byte[] decryptFromStorage(byte[] payload) throws GeneralSecurityException
    {
        if(payload.length < PBKDF2_SALT_BYTES + 4 + STORAGE_NONCE_BYTES + 4 + 1 + STORAGE_TAG_BYTES)
        {
            throw new GeneralSecurityException("Encrypted key payload is too short (corrupted file?)");
        }
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(payload);
        java.io.DataInputStream dis = new java.io.DataInputStream(bis);
        try
        {
            byte[] salt = new byte[PBKDF2_SALT_BYTES];
            dis.readFully(salt);
            int iterations = dis.readInt();
            byte[] nonce = new byte[STORAGE_NONCE_BYTES];
            dis.readFully(nonce);
            int ctLen = dis.readInt();
            if(ctLen < 0 || ctLen > payload.length)
            {
                throw new GeneralSecurityException("Invalid ciphertext length in encrypted key");
            }
            byte[] ciphertext = new byte[ctLen];
            dis.readFully(ciphertext);
            byte[] tag = new byte[STORAGE_TAG_BYTES];
            dis.readFully(tag);

            SecretKey storageKey = deriveStorageKey(salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, storageKey,
                    new GCMParameterSpec(GCM_TAG_BITS, nonce));

            byte[] combined = new byte[ctLen + STORAGE_TAG_BYTES];
            System.arraycopy(ciphertext, 0, combined, 0, ctLen);
            System.arraycopy(tag, 0, combined, ctLen, STORAGE_TAG_BYTES);
            return cipher.doFinal(combined);
        }
        catch(AEADBadTagException e)
        {
            throw new GeneralSecurityException(
                    "Cannot decrypt private key — wrong machine or user. " +
                    "If you changed your home directory or username, restore the key from backup.", e);
        }
        catch(IOException e)
        {
            throw new GeneralSecurityException("Failed to parse encrypted key payload", e);
        }
    }

    /**
     * 启动时迁移旧格式的未加密私钥 → 加密存储。
     * 只有 Cipher#init 需要 GeneralSecurityException 声明。
     */
    private void migrateUnencryptedKeys()
    {
        try
        {
            if(Files.notExists(privateKeyFile) || Files.size(privateKeyFile) == 0)
            {
                return;
            }
            String content = Files.readString(privateKeyFile).strip();
            // 已经是加密格式，跳过
            if(content.contains(PEM_ENC_PRIV_HEADER))
            {
                log.debug("Private key is already encrypted, no migration needed");
                return;
            }
            // PKCS#8 或 PKCS#1 未加密 — 读入并重写为加密格式
            if(content.contains(PEM_PRIV_PKCS8_HEADER)
                    || content.contains(PEM_PRIV_PKCS1_HEADER)
                    || content.contains(PEM_PUB_HEADER) == false)
            {
                log.info("Migrating unencrypted private key to encrypted storage...");
                PrivateKey key = parsePrivateKeyFromPem(content);
                String encryptedPem = toPemPrivateKey(key);
                writePrivateKeyFile(privateKeyFile, encryptedPem);
                log.info("Private key migration complete — stored as FST encrypted format");
            }
        }
        catch(Exception e)
        {
            log.warn("Failed to migrate private key to encrypted storage: {} — "
                    + "the key will be re-encrypted on next write", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  文件 I/O 辅助 — 创建时直接指定权限，无竞态窗口
    // ═══════════════════════════════════════════════════════════

    /**
     * 写入私钥文件。Unix 上创建时即指定 0600 权限，杜绝先写后 chmod 的窗口期。
     */
    private void writePrivateKeyFile(Path path, String content)
    {
        writeFileWithPermissions(path, content, PRIV_KEY_PERMS);
    }

    /**
     * 写入公钥文件。Unix 上创建时即指定 0644 权限。
     */
    private void writePublicKeyFile(Path path, String content)
    {
        writeFileWithPermissions(path, content, PUB_KEY_PERMS);
    }

    /**
     * 以指定 POSIX 权限创建文件并写入内容。
     * Windows 上忽略权限参数，退化为常规写文件。
     */
    private void writeFileWithPermissions(Path path, String content, Set<PosixFilePermission> perms)
    {
        try
        {
            Files.createDirectories(path.getParent());
            protectKeyDir(path.getParent());

            if(isWindows())
            {
                Files.writeString(path, content);
                return;
            }

            // Unix: 用 FileChannel + PosixFilePermissions 在创建时直接指定权限
            // Files.newByteChannel 在 CREATE_NEW/CREATE 时会先创建文件再设置权限——
            // 但在 CREATE_NEW 场景下，我们使用 CREATE + WRITE + TRUNCATE_EXISTING，
            // 配以 FileAttribute 来设置权限。实际上最安全的做法是：
            // 1. 先创建临时文件（仅 owner 可读写）
            // 2. 写入内容
            // 3. 原子 rename 到目标路径
            Path tmp = path.getParent().resolve(".tmp-" + path.getFileName() + "-" + UUID.randomUUID());
            try
            {
                Files.writeString(tmp, content,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                Files.setPosixFilePermissions(tmp, perms);
                Files.move(tmp, path,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            catch(Exception e)
            {
                Files.deleteIfExists(tmp);
                throw e;
            }
        }
        catch(IOException e)
        {
            throw new IllegalStateException("Failed to write key file: " + path, e);
        }
    }

    /**
     * 确保密钥目录只有 owner 可以访问 (0700)。
     * 仅在目录刚创建时或显式调用时生效，不影响已有文件的权限。
     */
    private void protectKeyDir(Path dir)
    {
        try
        {
            if(!isWindows() && Files.exists(dir) && Files.isDirectory(dir))
            {
                Files.setPosixFilePermissions(dir, KEY_DIR_PERMS);
            }
        }
        catch(Exception e)
        {
            log.warn("Unable to protect key directory path={} error=\"{}\"", dir, e.getMessage());
        }
    }

    /**
     * 启动时修正已存在密钥文件的权限（仅收紧，不放松）。
     * 如果公钥误设为 600 则收紧为 644，如果私钥误设为 644 则收紧为 600。
     */
    private void protectExistingKeyFiles()
    {
        if(isWindows())
        {
            return;
        }
        applyPermissionsIfExists(privateKeyFile, PRIV_KEY_PERMS);
        applyPermissionsIfExists(publicKeyFile, PUB_KEY_PERMS);
    }

    private void applyPermissionsIfExists(Path path, Set<PosixFilePermission> perms)
    {
        try
        {
            if(Files.exists(path))
            {
                Files.setPosixFilePermissions(path, perms);
            }
        }
        catch(Exception e)
        {
            log.warn("Unable to protect key file path={} error=\"{}\"", path, e.getMessage());
        }
    }

    private String readTextFile(Path path)
    {
        try
        {
            return Files.readString(path);
        }
        catch(IOException e)
        {
            throw new IllegalStateException("Failed to read file: " + path, e);
        }
    }

    private boolean deleteFileIfExists(Path path)
    {
        try
        {
            return Files.deleteIfExists(path);
        }
        catch(IOException e)
        {
            log.warn("Failed to delete file: {}", path, e);
            return false;
        }
    }

    private boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    // ═══════════════════════════════════════════════════════════
    //  辅助校验
    // ═══════════════════════════════════════════════════════════

    private void validateAes256Key(SecretKey aesKey) throws GeneralSecurityException
    {
        byte[] encoded = aesKey.getEncoded();
        if(encoded == null || encoded.length != AES_KEY_BITS / Byte.SIZE)
        {
            throw new GeneralSecurityException("AES-256 key must be 32 bytes");
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  最小 DER 解析器 (仅用于 PKCS#1 私钥)
    // ═══════════════════════════════════════════════════════════

    /**
     * Author: LQH
     * Date: 2026-07-03
     * Purpose: 解析 PKCS#1 RSAPrivateKey DER 结构 (TraditionalOpenSSL 格式)，
     *          将 RSA 参数 (n, e, d, p, q, dp, dq, qinv) 提取出来，
     *          用于兼容 Python crypto service 生成的旧格式密钥文件。
     *          仅支持 SEQUENCE + INTEGER 标签，不实现完整的 ASN.1 DER。
     */
    private static class DerReader
    {
        private final byte[] data;
        private int pos;

        DerReader(byte[] data)
        {
            this.data = data;
            this.pos = 0;
        }

        /** 当前读取位置 */
        int position()
        {
            return pos;
        }

        /** 读取并校验 DER tag 字节，不匹配则抛出异常 */
        void expectTag(int expected)
        {
            if(pos >= data.length)
            {
                throw new IllegalArgumentException("Unexpected DER EOF at position " + pos);
            }
            int tag = data[pos] & 0xff;
            if(tag != expected)
            {
                throw new IllegalArgumentException(
                        String.format("Expected DER tag 0x%02x but got 0x%02x at position %d",
                                expected, tag, pos));
            }
            pos++;
        }

        /** 读取 DER 长度字段 (短格式 < 128 或长格式 1-4 字节) */
        int readLength()
        {
            if(pos >= data.length)
            {
                throw new IllegalArgumentException("Unexpected DER EOF reading length");
            }
            int b = data[pos++] & 0xff;
            if(b < 0x80)
            {
                return b;
            }
            int numBytes = b & 0x7f;
            if(numBytes > 4)
            {
                throw new IllegalArgumentException("DER length too large: " + numBytes + " bytes");
            }
            long value = 0;
            for(int i = 0; i < numBytes; i++)
            {
                value = (value << 8) | (data[pos++] & 0xff);
            }
            if(value > Integer.MAX_VALUE)
            {
                throw new IllegalArgumentException("DER length exceeds int range");
            }
            return (int) value;
        }

        /** 读取 DER INTEGER → BigInteger (有符号大端) */
        BigInteger readInteger()
        {
            expectTag(0x02);
            int len = readLength();
            byte[] bytes = new byte[len];
            System.arraycopy(data, pos, bytes, 0, len);
            pos += len;
            return new BigInteger(bytes); // DER INTEGER is signed big-endian
        }

        /** 跳过指定字节数 (用于跳过已校验但无需解析的字段, 如 version) */
        void skip(int len)
        {
            pos += len;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  公钥工具（与 KeyArtifactPayload 的去重桥接）
    // ═══════════════════════════════════════════════════════════

    /**
     * Author: LQH
     * Date: 2026-07-03
     * Purpose: 公钥格式判断与转换工具。识别裸 Base64 公钥和 PEM 格式，
     *          自动将裸 Base64 包装为标准 PEM 供 KeyFactory 解析。
     */
    static final class KeyArtifactUtil
    {
        private KeyArtifactUtil() {}

        static boolean isBareBase64PublicKey(String value)
        {
            if(value == null || value.isBlank())
            {
                return false;
            }
            String stripped = value.strip();
            return !stripped.contains("-----") &&
                    stripped.matches("^[A-Za-z0-9+/=\\s]+$") &&
                    stripped.replaceAll("\\s", "").length() >= 32;
        }

        static String toPemPublicKey(String base64)
        {
            String body = base64.replaceAll("\\s", "");
            return PEM_PUB_HEADER + "\n" +
                    Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                            .encodeToString(Base64.getDecoder().decode(body)) +
                    "\n" + PEM_PUB_FOOTER + "\n";
        }
    }
}
