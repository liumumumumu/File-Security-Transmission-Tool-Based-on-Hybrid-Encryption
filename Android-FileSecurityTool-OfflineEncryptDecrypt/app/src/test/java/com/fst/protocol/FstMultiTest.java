package com.fst.protocol;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import static org.junit.Assert.*;

public class FstMultiTest {
    @Test
    public void readsSwiftEnvelopeAndAuthenticatesOtherRecipientEntries() throws Exception {
        Properties fixture = new Properties();
        try (var stream = getClass().getResourceAsStream("/fst-multi-swift.properties")) {
            fixture.load(stream);
        }
        KeyFactory factory = KeyFactory.getInstance("RSA");
        var key = (RSAPrivateCrtKey)factory.generatePrivate(new PKCS8EncodedKeySpec(
                Base64.getDecoder().decode(fixture.getProperty("fixturePrivateKey"))));
        var pub = factory.generatePublic(new RSAPublicKeySpec(key.getModulus(),key.getPublicExponent()));
        FstMulti.Unwrap unwrap = ciphertext -> {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new OAEPParameterSpec("SHA-256","MGF1",
                    MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
            return cipher.doFinal(ciphertext);
        };
        assertEquals("你好🌍", FstMulti.decryptText(fixture.getProperty("text"),pub,unwrap));
        var output = new ByteArrayOutputStream();
        long size = FstMulti.decryptFile(new ByteArrayInputStream(Base64.getDecoder().decode(fixture.getProperty("file"))),
                pub,unwrap,name -> { assertEquals("共享.txt",name); return output; }, (done,total) -> {});
        assertEquals(10,size);
        assertEquals("你好🌍",new String(output.toByteArray(),StandardCharsets.UTF_8));
        var other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        var text = FstMulti.encryptText("secret",List.of(pub,other.getPublic(),pub));
        var body = Base64.getUrlDecoder().decode(text.substring(FstMulti.TEXT.length()));
        assertEquals(2,ByteBuffer.wrap(body,40,4).getInt());
        assertEquals("secret",FstMulti.decryptText(text,pub,unwrap));
        var stranger = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        assertThrows(IOException.class,() -> FstMulti.decryptText(text,stranger,b -> {
            throw new AssertionError("An unlisted recipient must never attempt RSA decryption");
        }));
        byte[] modifiedTable = body.clone();
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        sha.update(java.util.Arrays.copyOfRange(body,8,40));
        byte[] localId = sha.digest(pub.getEncoded());
        int firstEntry = 44;
        int firstWidth = ByteBuffer.wrap(body,firstEntry+32,4).getInt();
        boolean localFirst = java.util.Arrays.equals(localId,java.util.Arrays.copyOfRange(body,firstEntry,firstEntry+32));
        int otherEntry = localFirst ? firstEntry + 36 + firstWidth : firstEntry;
        modifiedTable[otherEntry + 36] ^= 1;
        String tampered = FstMulti.TEXT + Base64.getUrlEncoder().withoutPadding().encodeToString(modifiedTable);
        assertThrows(javax.crypto.AEADBadTagException.class,() -> FstMulti.decryptText(tampered,pub,unwrap));
        // Damage the final tag. No plaintext can be released.
        body[body.length-1] ^= 1;
        String corrupted = FstMulti.TEXT + Base64.getUrlEncoder().withoutPadding().encodeToString(body);
        assertThrows(javax.crypto.AEADBadTagException.class,() -> FstMulti.decryptText(corrupted,pub,unwrap));
    }

    @Test
    public void enforcesRecipientAndTextLimitsAfterDeduplication() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096);
        var base = (java.security.interfaces.RSAPublicKey)generator.generateKeyPair().getPublic();
        var factory = KeyFactory.getInstance("RSA");
        var keys = new ArrayList<PublicKey>();
        // Synthetic distinct public keys exercise the size ceiling without storing 256 private keys.
        for (int i=0;i<256;i++) {
            keys.add(factory.generatePublic(new RSAPublicKeySpec(base.getModulus(),
                    base.getPublicExponent().add(BigInteger.valueOf(2L*i)))));
        }
        keys.add(base);
        String plain = "🌍".repeat(4096);
        assertEquals(209002,FstMulti.encryptText(plain,keys).length());
        assertThrows(IOException.class,() -> FstMulti.encryptText(plain+"x",keys));
        keys.add(factory.generatePublic(new RSAPublicKeySpec(base.getModulus(),BigInteger.valueOf(17))));
        assertThrows(IOException.class,() -> FstMulti.encryptText("x",keys));
        assertThrows(IOException.class,() -> FstMulti.encryptText("x",List.of()));
    }
}
