package com.crypto;

import com.common.crypto.AesGcmChunk;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.Assert.*;

public class CryptoEngineCompatibilityTest
{
    private static final OAEPParameterSpec PYTHON_OAEP = new OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final PSSParameterSpec PYTHON_PSS = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    @Test
    public void pythonStyleOaepEncryptsAndJavaDecrypts() throws Exception
    {
        KeyPair pair = rsaPair();
        byte[] plain = "python-to-java".getBytes(StandardCharsets.UTF_8);
        Cipher pythonStyle = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        pythonStyle.init(Cipher.ENCRYPT_MODE, pair.getPublic(), PYTHON_OAEP);
        byte[] ciphertext = pythonStyle.doFinal(plain);

        assertArrayEquals(plain, new CryptoEngine().rsaOaepDecrypt(ciphertext, pair.getPrivate()));
    }

    @Test
    public void javaOaepEncryptsAndPythonStyleDecrypts() throws Exception
    {
        KeyPair pair = rsaPair();
        byte[] plain = "java-to-python".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new CryptoEngine().rsaOaepEncrypt(plain, pair.getPublic());
        Cipher pythonStyle = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        pythonStyle.init(Cipher.DECRYPT_MODE, pair.getPrivate(), PYTHON_OAEP);

        assertArrayEquals(plain, pythonStyle.doFinal(ciphertext));
    }

    @Test
    public void javaPssSignatureUsesPythonParameters() throws Exception
    {
        KeyPair pair = rsaPair();
        String data = "cross-language-signature";
        String encoded = new CryptoEngine().signUtf8ToBase64(data, pair.getPrivate());
        Signature verifier = Signature.getInstance("RSASSA-PSS");
        verifier.setParameter(PYTHON_PSS);
        verifier.initVerify(pair.getPublic());
        verifier.update(data.getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(encoded)));
    }

    @Test
    public void aesGcmMatchesPythonLayoutAndDetectsTampering() throws Exception
    {
        CryptoEngine engine = new CryptoEngine();
        SecretKey key = engine.generateAes256Key();
        byte[] plain = "AESGCM returns ciphertext plus a 16-byte tag".getBytes(StandardCharsets.UTF_8);
        AesGcmChunk encrypted = engine.encryptGcm(plain, key);

        assertEquals(12, encrypted.nonce().length);
        assertEquals(16, encrypted.tag().length);
        assertArrayEquals(plain, engine.decryptGcm(
                encrypted.nonce(), encrypted.ciphertext(), encrypted.tag(), key, null));

        byte[] damaged = encrypted.ciphertext().clone();
        damaged[0] ^= 1;
        assertThrows(javax.crypto.AEADBadTagException.class,
                () -> engine.decryptGcm(encrypted.nonce(), damaged, encrypted.tag(), key, null));
    }

    @Test
    public void fingerprintHashesExactPemUtf8Bytes() throws Exception
    {
        String pem = "-----BEGIN PUBLIC KEY-----\nYWJj\n-----END PUBLIC KEY-----\n";
        String expected = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(pem.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, new CryptoEngine().fingerprintPemText(pem));
        assertNotEquals(expected, new CryptoEngine().fingerprintPemText(pem.stripTrailing()));
    }

    @Test
    public void pkcs1ExportCanBeImportedAgain() throws Exception
    {
        KeyMaterialCodec codec = new KeyMaterialCodec();
        KeyPair pair = rsaPair();
        String exported = codec.exportPkcs1PrivateKey(pair.getPrivate());

        assertTrue(exported.startsWith("-----BEGIN RSA PRIVATE KEY-----"));
        assertEquals(pair.getPrivate(), codec.parsePrivateKey(exported));
    }

    private static KeyPair rsaPair() throws Exception
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
