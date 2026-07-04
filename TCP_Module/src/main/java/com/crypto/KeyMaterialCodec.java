package com.crypto;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;

/**
 * PEM/PKCS codec for Python-compatible RSA key material.
 */
public final class KeyMaterialCodec
{
    private final JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

    public PrivateKey parsePrivateKey(String pem)
    {
        try(PEMParser parser = new PEMParser(new StringReader(pem)))
        {
            Object object = parser.readObject();
            if(object instanceof PEMKeyPair pair) return converter.getKeyPair(pair).getPrivate();
            if(object instanceof PrivateKeyInfo info) return converter.getPrivateKey(info);
            throw new IllegalArgumentException("PEM does not contain a supported RSA private key");
        }
        catch(Exception ex)
        {
            throw new IllegalArgumentException("Failed to parse private key PEM", ex);
        }
    }

    public PublicKey parsePublicKey(String pem)
    {
        try(PEMParser parser = new PEMParser(new StringReader(pem)))
        {
            Object object = parser.readObject();
            if(object instanceof org.bouncycastle.asn1.x509.SubjectPublicKeyInfo info)
                return converter.getPublicKey(info);
            throw new IllegalArgumentException("PEM does not contain an X.509 public key");
        }
        catch(Exception ex)
        {
            throw new IllegalArgumentException("Failed to parse public key PEM", ex);
        }
    }

    public String exportPkcs1PrivateKey(PrivateKey privateKey)
    {
        try
        {
            if(!(privateKey instanceof RSAPrivateCrtKey rsa))
                throw new IllegalArgumentException("Private key is not an RSA CRT key");
            RSAPrivateKey pkcs1 = new RSAPrivateKey(
                    rsa.getModulus(), rsa.getPublicExponent(), rsa.getPrivateExponent(),
                    rsa.getPrimeP(), rsa.getPrimeQ(), rsa.getPrimeExponentP(),
                    rsa.getPrimeExponentQ(), rsa.getCrtCoefficient());
            return writePem(new PemObject("RSA PRIVATE KEY", pkcs1.getEncoded()));
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Failed to encode PKCS#1 private key", ex);
        }
    }

    private String writePem(PemObject object)
    {
        try
        {
            StringWriter output = new StringWriter();
            try(PemWriter writer = new PemWriter(output))
            {
                writer.writeObject(object);
            }
            return output.toString().replace("\r\n", "\n");
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Failed to encode PEM", ex);
        }
    }
}
