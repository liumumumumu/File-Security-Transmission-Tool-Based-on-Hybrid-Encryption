package com.fst.protocol;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class FstMulti {
    private FstMulti() {}
    public static final String TEXT = "FST-TEXT2:";
    private static final int BLOCK = 1 << 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    public interface Unwrap { byte[] apply(byte[] ciphertext) throws Exception; }
    public interface Destination { OutputStream open(String name) throws Exception; }
    public interface Progress { void update(long done, long total) throws Exception; }
    private static class Header {
        byte[] key, bytes, prefix;
        Header(byte[] key, byte[] bytes, byte[] prefix) {
            this.key = key; this.bytes = bytes; this.prefix = prefix;
        }
    }
    private static void check(boolean ok, String message) throws IOException {
        if (!ok) throw new IOException(message);
    }
    private static byte[] random(int n) {
        byte[] bytes = new byte[n]; RANDOM.nextBytes(bytes); return bytes;
    }
    private static byte[] word(int n) { return ByteBuffer.allocate(4).putInt(n).array(); }
    private static byte[] join(byte[]... fields) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] f : fields) out.write(f, 0, f.length);
        return out.toByteArray();
    }
    private static byte[] read(DataInputStream in, int n) throws IOException {
        byte[] field = new byte[n]; in.readFully(field); return field;
    }
    private static byte[] recipientId(byte[] salt, PublicKey publicKey) throws GeneralSecurityException {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent());
        byte[] canonical = KeyFactory.getInstance("RSA").generatePublic(spec).getEncoded();
        return digest(salt, canonical);
    }
    private static byte[] digest(byte[]... fields) throws GeneralSecurityException {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (byte[] f : fields) hash.update(f);
        return hash.digest();
    }
    private static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int diff = (a[i] & 255) - (b[i] & 255);
            if (diff != 0) return diff;
        }
        return 0;
    }
    private static byte[] magic(boolean text) {
        return new byte[]{70,83,84,(byte)(text ? 84 : 51),1,1,1,1};
    }
    private static boolean allowed(int bytes) { return bytes == 256 || bytes == 384 || bytes == 512; }
    private static Header create(List<PublicKey> keys, boolean text) throws Exception {
        byte[] secret = random(32), salt = random(32), prefix = random(8);
        SortedMap<byte[],byte[]> table = new TreeMap<>(FstMulti::compare);
        int index = 0;
        for (PublicKey key : keys) {
            index++;
            try {
                check(key instanceof RSAPublicKey && allowed((((RSAPublicKey) key).getModulus().bitLength()+7)/8), "Unsupported RSA size");
                byte[] id = recipientId(salt, key);
                if (table.containsKey(id)) continue;
                check(table.size() < 256, "Maximum 256 recipients");
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA256,PSource.PSpecified.DEFAULT));
                table.put(id, cipher.doFinal(secret));
            } catch (Exception ex) { throw new IOException("Recipient " + index + ": " + ex.getMessage(), ex); }
        }
        check(!table.isEmpty(), "Select at least one recipient");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.write(magic(text)); out.write(salt); out.writeInt(table.size());
        for (Map.Entry<byte[],byte[]> entry : table.entrySet()) {
            out.write(entry.getKey()); out.writeInt(entry.getValue().length); out.write(entry.getValue());
        }
        out.write(prefix);
        return new Header(secret, buf.toByteArray(), prefix);
    }
    private static Header parse(DataInputStream in, boolean text, PublicKey local, Unwrap unwrap) throws Exception {
        byte[] magic = read(in,8);
        check(Arrays.equals(magic,magic(text)), "Unsupported multi-recipient format");
        byte[] salt = read(in,32), id = recipientId(salt, local);
        int count = in.readInt();
        check(count >= 1 && count <= 256,"Invalid recipient count");
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buf);
        out.write(magic); out.write(salt); out.writeInt(count);
        byte[] prev = null, match = null;
        for (int i=0;i<count;i++) {
            byte[] next = read(in,32);
            check(prev == null || compare(prev,next)<0,"Invalid recipient order");
            prev = next;
            int n = in.readInt(); check(allowed(n),"Invalid RSA length");
            byte[] wrapped = read(in,n);
            out.write(next); out.writeInt(n); out.write(wrapped);
            if (Arrays.equals(id,next)) match = wrapped;
        }
        byte[] prefix = read(in,8); out.write(prefix);
        check(match != null,"No entry matches the current public key");
        byte[] key = unwrap.apply(match);
        check(key.length == 32,"Invalid session key");
        return new Header(key,buf.toByteArray(),prefix);
    }
    private static byte[] crypt(Header h, int mode, int index, byte[] aad, byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(h.key,"AES"),new GCMParameterSpec(128,join(h.prefix,word(index))));
        cipher.updateAAD(aad); return cipher.doFinal(data);
    }
    private static String utf8(byte[] bytes) throws IOException {
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static String encryptText(String text,List<PublicKey> recipients) throws Exception {
        byte[] plain = text.getBytes(StandardCharsets.UTF_8);
        check(plain.length>0 && plain.length<=16384,"Text must be 1–16384 UTF-8 bytes");
        Header h = create(recipients,true);
        byte[] aad = join(h.bytes,word(plain.length));
        return TEXT + Base64.getUrlEncoder().withoutPadding().encodeToString(join(aad,crypt(h,Cipher.ENCRYPT_MODE,0,aad,plain)));
    }
    public static String decryptText(String text,PublicKey local,Unwrap unwrap) throws Exception {
        String value = text.trim();
        check(value.startsWith(TEXT) && value.length()<=208992+TEXT.length(),"Invalid text envelope");
        String encoded = value.substring(TEXT.length());
        byte[] body = Base64.getUrlDecoder().decode(encoded);
        check(Base64.getUrlEncoder().withoutPadding().encodeToString(body).equals(encoded),"Invalid Base64URL");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
        Header h = parse(in,true,local,unwrap);
        int n = in.readInt(); check(n>0 && n<=16384 && in.available()==n+16,"Invalid text length");
        return utf8(crypt(h,Cipher.DECRYPT_MODE,0,join(h.bytes,word(n)),read(in,n+16)));
    }
    private static void size(long size) throws IOException {
        check(size>=0 && size <= (long)Integer.MAX_VALUE*BLOCK,"Invalid file size");
    }
    public static void encryptFile(InputStream input,OutputStream output,long size,String name,List<PublicKey> keys,Progress progress) throws Exception {
        size(size);
        byte[] filename = name.getBytes(StandardCharsets.UTF_8);
        check(filename.length>0 && filename.length<=4096,"Invalid filename");
        Header h = create(keys,false);
        byte[] meta = join(ByteBuffer.allocate(8).putLong(size).array(),filename);
        byte[] aad = join(h.bytes,word(meta.length));
        output.write(aad); output.write(crypt(h,Cipher.ENCRYPT_MODE,0,aad,meta));
        blocks(new DataInputStream(input),output,h,size,true,progress);
    }
    public static long decryptFile(InputStream input,PublicKey local,Unwrap unwrap,Destination destination,Progress progress) throws Exception {
        DataInputStream in = new DataInputStream(input);
        Header h = parse(in,false,local,unwrap);
        int n = in.readInt(); check(n>=9 && n<=4104,"Invalid file header");
        byte[] meta = crypt(h,Cipher.DECRYPT_MODE,0,join(h.bytes,word(n)),read(in,n+16));
        long size = ByteBuffer.wrap(meta).getLong(); size(size);
        String name = utf8(Arrays.copyOfRange(meta,8,meta.length));
        try (OutputStream out = destination.open(name)) { blocks(in,out,h,size,false,progress); }
        return size;
    }
    private static void blocks(DataInputStream in,OutputStream out,Header h,long size,boolean encrypt,Progress progress) throws Exception {
        byte[] hash = digest(h.bytes);
        long done = 0;
        for (int i=1;done<size;i++) {
            int n = (int)Math.min(BLOCK,size-done);
            progress.update(done,size);
            byte[] data = read(in,n+(encrypt?0:16));
            out.write(crypt(h,encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,i,join(hash,word(i),word(n)),data));
            done += n;
        }
        check(in.read()==-1,"Unexpected trailing bytes"); progress.update(size,size);
    }
}
