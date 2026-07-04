package com.crypto.exception;

public class KeyMismatchException extends CryptoOperationException
{
    public KeyMismatchException(Throwable cause)
    {
        super("Unable to decrypt using the current private key with RSA-OAEP SHA-256/MGF1-SHA256. "
                + "Possible causes include a different key pair, damaged ciphertext, or an incompatible sender version.",
                cause);
    }
}
