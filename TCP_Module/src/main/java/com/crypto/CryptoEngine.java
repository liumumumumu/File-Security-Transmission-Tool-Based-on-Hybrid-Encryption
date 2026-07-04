package com.crypto;

import com.common.crypto.AesGcmChunk;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Stateless local cryptographic operations compatible with the former Python
 * cryptography implementation.
 */
public final class CryptoEngine
{
    public static final int AES_KEY_BYTES = 32;
    public static final int GCM_NONCE_BYTES = 12;
    public static final int GCM_TAG_BYTES = 16;
    private static final int GCM_TAG_BITS = GCM_TAG_BYTES * 8;
    private static final OAEPParameterSpec OAEP_SHA256 = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final PSSParameterSpec PSS_SHA256 = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    private final SecureRandom secureRandom;

    public CryptoEngine()
    {
        this(new SecureRandom());
    }

    CryptoEngine(SecureRandom secureRandom)
    {
        this.secureRandom = secureRandom;
    }

    public SecretKey generateAes256Key() throws GeneralSecurityException
    {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_BYTES * 8, secureRandom);
        return generator.generateKey();
    }

    public byte[] rsaOaepEncrypt(byte[] plain, PublicKey publicKey) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256, secureRandom);
        return cipher.doFinal(plain);
    }

    public byte[] rsaOaepDecrypt(byte[] ciphertext, PrivateKey privateKey) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
        return cipher.doFinal(ciphertext);
    }

    public String signUtf8ToBase64(String text, PrivateKey privateKey) throws GeneralSecurityException
    {
        Signature signature = Signature.getInstance("RSASSA-PSS");
        signature.setParameter(PSS_SHA256);
        signature.initSign(privateKey, secureRandom);
        signature.update(text.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public boolean verifyUtf8Base64(String publicKeyText, String text, String signatureBase64,
                                    java.util.function.Function<String, PublicKey> keyParser)
    {
        try
        {
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(PSS_SHA256);
            signature.initVerify(keyParser.apply(publicKeyText));
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        }
        catch(Exception ignored)
        {
            return false;
        }
    }

    public AesGcmChunk encryptGcm(byte[] plain, SecretKey key, byte[] nonce, byte[] aad)
            throws GeneralSecurityException
    {
        validateGcm(key, nonce, null);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if(aad != null && aad.length > 0) cipher.updateAAD(aad);
        byte[] encrypted = cipher.doFinal(plain);
        int split = encrypted.length - GCM_TAG_BYTES;
        return new AesGcmChunk(nonce, Arrays.copyOfRange(encrypted, 0, split),
                Arrays.copyOfRange(encrypted, split, encrypted.length));
    }

    public AesGcmChunk encryptGcm(byte[] plain, SecretKey key) throws GeneralSecurityException
    {
        byte[] nonce = new byte[GCM_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return encryptGcm(plain, key, nonce, null);
    }

    public byte[] decryptGcm(byte[] nonce, byte[] ciphertext, byte[] tag, SecretKey key, byte[] aad)
            throws GeneralSecurityException
    {
        validateGcm(key, nonce, tag);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        if(aad != null && aad.length > 0) cipher.updateAAD(aad);
        byte[] combined = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
        System.arraycopy(tag, 0, combined, ciphertext.length, tag.length);
        return cipher.doFinal(combined);
    }

    public String fingerprintPemText(String publicKeyPem) throws GeneralSecurityException
    {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(publicKeyPem.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static void validateGcm(SecretKey key, byte[] nonce, byte[] tag)
            throws GeneralSecurityException
    {
        if(key == null || key.getEncoded() == null || key.getEncoded().length != AES_KEY_BYTES)
            throw new GeneralSecurityException("AES-256 key must be 32 bytes");
        if(nonce == null || nonce.length != GCM_NONCE_BYTES)
            throw new GeneralSecurityException("AES-GCM nonce must be 12 bytes");
        if(tag != null && tag.length != GCM_TAG_BYTES)
            throw new GeneralSecurityException("AES-GCM tag must be 16 bytes");
    }
}
