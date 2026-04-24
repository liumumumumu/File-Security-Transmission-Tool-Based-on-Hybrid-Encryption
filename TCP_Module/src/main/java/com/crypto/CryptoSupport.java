package com.crypto;

import com.common.crypto.AesGcmChunk;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;

@Component
public class CryptoSupport  //负责加密，解密，签名，验证签名，密钥处理；但是真正的实现是由Python cryptography库实现，并部署在本地，因此这个类负责调用相关的服务
{
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BITS = 12;

    private final SecureRandom secureRandom = new SecureRandom();//随机数生成器（待定）
    private KeyPair keyPair; //非对称密钥对（待定）



    //当Spring 创建好对象，并把依赖都注入完成后，自动调用
    @PostConstruct  //初始化回调注解
    private void init()
    {

    }

    public String getEncodedPublicKey()
    {
        return null;
    }

    public String getEncodedPrivateKey()
    {
        return null;
    }

    //将生成签名的二进制字节数组，编码成Base64字符串
    public String signToBase64(String payload)throws GeneralSecurityException       //过程是用私钥对原始数据进行签名，得到签名字节byte数组，再把byte数组转换成Base64文本
    {
        return null;
    }

    //验证签名
    public boolean verifySignature(String publicKeyBase64, String payload, String signatureBase64)
    {
        return false;
    }

    //生成AES算法用的密钥（待定）
    public SecretKey generateAesKey()throws GeneralSecurityException
    {
        return null;
    }

    public String encryptKeyForReceiver(SecretKey secretKey, String receivePublicKeyBase64)throws GeneralSecurityException
    {
        return null;
    }

    //将收到的，经过非对称加密的AES密钥解密出来
    public SecretKey decryptAesKey(String encryptedKeyBase64)throws GeneralSecurityException
    {
        return null;
    }

    public AesGcmChunk encryptChunk


}
