package com.client.direct.qr;

import com.common.crypto.AesGcmChunk;
import com.crypto.CryptoSupport;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: LQH
 * Date: 2026-05-19
 * Purpose: 负责将IPv6直连握手需要交换的信息编码成二维码文本，以及二维码文本中解码，验签，解密还原成对象
 * FST1: 是固定前缀，用来标识这是本系统的握手二维码
 * 内容部分是CBOR二进制数据
 * CBOR外面再用Base45编码
 * 数据内部附有签名，防止二维码内容被篡改
 * 接收方的响应里面还有加密载荷，尽量不泄漏连接信息
 *
 **/

@Service
public class DirectQrCodec
{
    public static final String PREFIX="FST1:";//自定义的一种二维码文本格式,用于IPv6直连握手,本项目的握手QR code都要以这个为开头
    private static final int PROTOCOL_VERSION = 1;//协议版本号
    private final CryptoSupport cryptoSupport;

    public DirectQrCodec(CryptoSupport cryptoSupport)
    {
        this.cryptoSupport = cryptoSupport;
    }

    //发送方邀请信息编码，将发送方邀请信息对象编码成二维码字符串
    public String encodeSenderOffer(SenderOfferQr offer) throws GeneralSecurityException
    {
        Map<String, Object> signed = senderOfferSignedFields(offer, null);//先构造不带签名的字段；type; protocolVersion; inviteId; senderAccountId; senderDeviceId; senderPublicKey; expiresAt
        String signature = sign(signed);//对这些字段进行签名
        Map<String, Object> root = senderOfferSignedFields(offer, signature);//将签名放回字段里面
        return PREFIX + Base45.encode(CborLite.encodeCanonical(root));
    }

    //发送方邀请信息解码，从二维码中还原出SenderOfferQr
    public SenderOfferQr decodeSenderOffer(String text) throws GeneralSecurityException
    {
        Map<String, Object> root = decodeRoot(text);//先检查前缀，解Base45, 解CBOR
        requireType(root, "sender-offer");//type必须是sender-offer
        verifySignature(root, string(root, "senderPublicKey"));//验证签名
        String publicKey = string(root, "senderPublicKey");
        String accountId = cryptoSupport.publicKeyFingerprint(publicKey);
        if(!accountId.equals(string(root, "senderAccountId")))//判断二维码声明的senderAccountId必须等于该公钥计算出的指纹
        {
            throw new GeneralSecurityException("Sender accountId does not match public key");
        }
        return new SenderOfferQr(
                string(root, "inviteId"),
                string(root, "senderAccountId"),
                string(root, "senderDeviceId"),
                publicKey,
                Instant.parse(string(root, "expiresAt")),
                string(root, "signature")
        );
    }

    //接收方响应信息编码，敏感连接信息需要加密
    public String encodeReceiverResponse(ReceiverResponseQr response, String senderPublicKey) throws GeneralSecurityException
    {
        Map<String, Object> inner = new LinkedHashMap<>();//内部的明文载荷
        inner.put("inviteId", response.getInvited());//邀请任务
        inner.put("ipv6AddressCandidates", response.getIpv6AddressCandidates());//IPv6候选地址
        inner.put("port", response.getPort());//监听的端口
        inner.put("connectionNonce", response.getConnectionNonce());//连接挑战值
        inner.put("expiresAt", response.getExpiresAt().toString());//设置过期时间

        SecretKey key = cryptoSupport.generateAESKey();//生成AES密钥
        AesGcmChunk encrypted = cryptoSupport.encryptChunk(CborLite.encodeCanonical(inner), key);//用AES-GCM加密内部载荷
        String encryptedKey = cryptoSupport.encryptAESKeyForReceiver(key, senderPublicKey);//用发送方公钥加密AES密钥

        Map<String, Object> envelope = new LinkedHashMap<>();//构造加密信封
        envelope.put("alg", "RSA-OAEP-SHA256+A256GCM");
        envelope.put("encryptedKey", Base64.getDecoder().decode(encryptedKey));
        envelope.put("nonce", encrypted.nonce());
        envelope.put("ciphertext", encrypted.ciphertext());
        envelope.put("tag", encrypted.tag());

        Map<String, Object> unsigned = receiverResponseSignedFields(response, envelope, null);
        String signature = sign(unsigned);
        Map<String, Object> root = receiverResponseSignedFields(response, envelope, signature);
        return PREFIX + Base45.encode(CborLite.encodeCanonical(root));
    }

    //接收方响应信息解码
    public ReceiverResponseQr decodeReceiverResponse(String text) throws GeneralSecurityException
    {
        Map<String, Object> root = decodeRoot(text);//解码并校验类型
        requireType(root, "receiver-response");
        verifySignature(root, string(root, "receiverPublicKey"));//验证签名

        //校验接收方账号
        String receiverPublicKey = string(root, "receiverPublicKey");
        String accountId = cryptoSupport.publicKeyFingerprint(receiverPublicKey);
        if(!accountId.equals(string(root, "receiverAccountId")))
        {
            throw new GeneralSecurityException("Receiver accountId does not match public key");
        }

        //取出加密信封
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) root.get("encryptedPayload");
        SecretKey key = cryptoSupport.decryptAESKey(Base64.getEncoder().encodeToString(bytes(envelope, "encryptedKey")));//解密AES密钥
        byte[] plain = cryptoSupport.decryptChunk( //用AES-GCM解密内部载荷
                bytes(envelope, "nonce"),
                bytes(envelope, "ciphertext"),
                bytes(envelope, "tag"),
                key
        );
        Map<String, Object> inner = CborLite.decodeMap(plain);//解析出来的明文再用CBOR map解析
        return new ReceiverResponseQr(
                string(root, "inviteId"),
                string(root, "receiverAccountId"),
                string(root, "receiverDeviceId"),
                receiverPublicKey,
                Instant.parse(string(root, "expiresAt")),
                stringList(inner, "ipv6AddressCandidates"),
                (int) number(inner, "port"),
                string(inner, "connectionNonce"),
                string(root, "signature")
        );
    }

    //将发送方的邀请信息对象转换成Map对象
    private Map<String, Object> senderOfferSignedFields(SenderOfferQr offer, String signature)
    {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "sender-offer");
        root.put("protocolVersion", PROTOCOL_VERSION);
        root.put("inviteId", offer.getInviteId());
        root.put("senderAccountId", offer.getSenderAccountId());
        root.put("senderDeviceId", offer.getSenderDeviceId());
        root.put("senderPublicKey", offer.getSenderPublicKey());
        root.put("expiresAt", offer.getExpiresAt().toString());
        if(signature != null)
        {
            root.put("signature", signature);
        }
        return root;
    }

    //将接收方的响应信息对象转换成Map对象
    private Map<String, Object> receiverResponseSignedFields(ReceiverResponseQr response, Map<String, Object> envelope, String signature)
    {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "receiver-response");
        root.put("protocolVersion", PROTOCOL_VERSION);
        root.put("inviteId", response.getInvited());
        root.put("receiverAccountId", response.getReceiverAccountId());
        root.put("receiverDeviceId", response.getReceiverDeviceId());
        root.put("receiverPublicKey", response.getReceiverPublicKey());
        root.put("expiresAt", response.getExpiresAt().toString());
        root.put("encryptedPayload", envelope);
        if(signature != null)
        {
            root.put("signature", signature);
        }
        return root;
    }

    //实现签名的函数;1.将字段编码成canonical CBOR;2.再把CBOR字节转成Base64字符串;3.对Base64字符串进行签名;4.返回Base64格式的签名
    private String sign(Map<String, Object> fields) throws GeneralSecurityException
    {
        byte[] canonical = CborLite.encodeCanonical(fields);
        return cryptoSupport.signToBase64(Base64.getEncoder().encodeToString(canonical));
    }

    //验证二维码内容的数字签名，确认二维码没有被篡改
    private void verifySignature(Map<String, Object> root, String publicKey) throws GeneralSecurityException
    {
        String signature = string(root, "signature");//从二维码中读取签名字段
        Map<String, Object> unsigned = new LinkedHashMap<>(root);//复制一份二维码数据
        unsigned.remove("signature");//移除签名字段，因为验签的时候不能把signature字段本身也算进去
        String data = Base64.getEncoder().encodeToString(CborLite.encodeCanonical(unsigned));//把待验签的数据转换成签名时相同的格式
        if(!cryptoSupport.verifySignature(publicKey, data, signature))
        {
            throw new GeneralSecurityException("QR signature verification failed");
        }
    }

    //负责检查前缀，Base45解码，CBOR解码
    private Map<String, Object> decodeRoot(String text)
    {
        if(text == null || !text.startsWith(PREFIX))//如果二维码不以FST1开头，则直接拒绝
        {
            throw new IllegalArgumentException("QR payload must start with "+PREFIX);
        }
        return CborLite.decodeMap(Base45.decode(text.substring(PREFIX.length()).trim()));
    }

    //检查二维码类型和协议版本
    private void requireType(Map<String, Object> root, String expected)
    {
        String actual = string(root, "type");
        if(!expected.equals(actual))//防止用户将发送方邀请二维码当成接收方响应的二维码
        {
            throw new IllegalArgumentException("Expected QR type "+expected+", got "+actual);
        }
        if(number(root, "protocolVersion") != PROTOCOL_VERSION)//防止协议版本不兼容
        {
            throw new IllegalArgumentException("Unsupported QR protocol version");
        }
    }

    private String string(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(!(value instanceof String string))
        {
            throw new IllegalArgumentException("Missing string field: "+key);
        }
        return string;
    }

    private long number(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(value instanceof Number number)
        {
            return number.longValue();
        }
        throw new IllegalArgumentException("Missing numeric field: "+key);
    }

    private byte[] bytes(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(value instanceof byte[] bytes)
        {
            return bytes;
        }
        if(value instanceof String string)
        {
            return string.getBytes(StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Missing bytes field: "+key);
    }

    private List<String> stringList(Map<String, Object> map, String key)
    {
        Object value = map.get(key);
        if(!(value instanceof List<?> list))
        {
            throw new IllegalArgumentException("Missing list field: "+key);
        }
        return list.stream().map(String::valueOf).toList();
    }
}
