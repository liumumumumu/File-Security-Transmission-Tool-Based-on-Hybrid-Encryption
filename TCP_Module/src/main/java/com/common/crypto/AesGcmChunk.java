package com.common.crypto;

import java.util.Arrays;
import java.util.Objects;

public final class AesGcmChunk {
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final byte[] tag;

    public AesGcmChunk(byte[] nonce, byte[] ciphertext, byte[] tag) {
        this.nonce = nonce;
        this.ciphertext = ciphertext;
        this.tag = tag;
    }


    public byte[] nonce() {
        return nonce;
    }

    public byte[] ciphertext() {
        return ciphertext;
    }

    public byte[] tag() {
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AesGcmChunk that)) {
            return false;
        }
        return Objects.equals(nonce, that.nonce)
                && Objects.equals(ciphertext, that.ciphertext)
                && Objects.equals(tag, that.tag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nonce, ciphertext, tag);
    }//计算哈希值

    @Override
    public String toString() {
        return "AesGcmChunk[nonce=" + nonce
                + ", ciphertext=" + ciphertext
                + ", tag=" + tag + "]";
    }
}
