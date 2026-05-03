package com.crypto;

import com.common.config.CryptoKeyProperties;
import com.common.crypto.AesGcmChunk;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Base64;
import java.util.Map;

/**
 * Author: LQH
 * Date: 2026-04-26
 * Purpose: 用来提供一组混合加密相关的组件
 *
 * */

@Component
@Slf4j
public class CryptoSupport  //负责加密，解密，签名，验证签名，密钥处理；但是真正的实现是由Python cryptography库实现，并部署在本地，因此这个类负责调用相关的服务
{

    private final CryptoKeyProperties cryptoKeyProperties;
    @Value("${crypto-service.address}")
    private String CryptoServiceAddress;
    @Value("${crypto-service.port}")
    private String CryptoServicePort;
    private String CRYPTO_SERVICE_URL="http://"+CryptoServiceAddress+":"+CryptoServicePort;
    private final HttpClient httpClient=HttpClient.newHttpClient();
    private final Gson gson=new Gson();//序列化/ 反序列化工具

    //AES-GCM的认证标签长度128bit
    private static final int GCM_TAG_BITS = 128;

    //GCM随机nonce长度12字节
    private static final int GCM_NONCE_BITS = 12;

    //用于生产随机数，如nonce
    private final SecureRandom secureRandom = new SecureRandom();//随机数生成器（待定）

    //保存RSA公钥和私钥
    private KeyPair keyPair; //非对称密钥对（待定）

    public CryptoSupport(CryptoKeyProperties cryptoKeyProperties) {
        this.cryptoKeyProperties = cryptoKeyProperties;
    }


    //当Spring 创建好对象，并把依赖都注入完成后，自动调用
    @PostConstruct  //初始化回调注解
    public synchronized void init()
    {
        Path privateKeyPath=configuredPath(cryptoKeyProperties.getPrivateKeyPath());
        Path publicKeyPath=configuredPath(cryptoKeyProperties.getPublicKeyPath());
        if(Files.exists(privateKeyPath))
        {
            loadKeyPair(privateKeyPath, publicKeyPath);
            return;
        }
        generateAndPersistKeyPair(privateKeyPath, publicKeyPath);
    }

    //返回公钥的Base64字符串
    public String getEncodedPublicKey()
    {
//        return null;

        return GET("/key/public").get("publicKey");
    }

    //返回私钥的Base64字符串
    public String getEncodedPrivateKey()
    {
//        return null;

        return GET("/key/private").get("privateKey");
    }

    //将生成签名的二进制字节数组，编码成Base64字符串
    public String signToBase64(String challenge)throws GeneralSecurityException       //过程是用私钥对原始数据进行签名，得到签名字节byte数组，再把byte数组转换成Base64文本
    {
//        return null;

        return POST("/sign", Map.of("data", challenge)).get("signature");
    }

    //验证签名
    public boolean verifySignature(String publicKeyBase64, String challenge, String signatureBase64)
    {
//        return false;

        Map<String, String> result=POST("/verify", Map.of(
                "publicKey", publicKeyBase64,
                "data", challenge,
                "signature", signatureBase64
        ));
        return Boolean.parseBoolean(result.get("valid"));
    }

    //生成AES算法用的密钥（待定）
    public SecretKey generateAESKey()throws GeneralSecurityException
    {
//        return null;

        String keyBase64=POST("/aes/generate", Map.of()).get("key");
        return new SecretKeySpec(Base64.getDecoder().decode(keyBase64),"AES");
    }

    //用接收方的RSA公钥加密
    public String encryptAESKeyForReceiver(SecretKey AESKey, String receivePublicKeyBase64)throws GeneralSecurityException
    {
//        return null;

        return POST("rsa/encrypt", Map.of(
                "publicKey", receivePublicKeyBase64,
                "plain", Base64.getEncoder().encodeToString(AESKey.getEncoded())
        )).get("cipher");
    }

    //将收到的，经过非对称加密的AES密钥解密出来
    public SecretKey decryptAESKey(String encryptedAESKeyBase64)throws GeneralSecurityException
    {
//        return null;

        String keyBase64=POST("/rsa/decrypt", Map.of(
                "cipher", encryptedAESKeyBase64
        )).get("plain");
        return new SecretKeySpec(Base64.getDecoder().decode(keyBase64),"AES");
    }

    //加密一段字节数据
    public AesGcmChunk encryptChunk(byte[] plain, SecretKey AESKey)throws GeneralSecurityException
    {
//        return null;

        Map<String, String>result=POST("/aes-gcm/encrypt", Map.of(
                "key", Base64.getEncoder().encodeToString(AESKey.getEncoded()),
                "plain", Base64.getEncoder().encodeToString(plain)
        ));

        return new AesGcmChunk(
                Base64.getDecoder().decode(result.get("nonce")),
                Base64.getDecoder().decode(result.get("ciphertext")),
                Base64.getDecoder().decode(result.get("tag"))
        );
    }

    //解密AES-GCM加密的数据块
    public byte[] decryptChunk(byte[] nonce, byte[] ciphertext, byte[] tag, SecretKey AESKey)throws GeneralSecurityException
    {
//        return null;

        Map<String, String> result = POST("/aes-gcm/decrypt", Map.of(
                "key", Base64.getEncoder().encodeToString(AESKey.getEncoded()),
                "nonce", Base64.getEncoder().encodeToString(nonce),
                "ciphertext", Base64.getEncoder().encodeToString(ciphertext),
                "tag", Base64.getEncoder().encodeToString(tag)
        ));
        return Base64.getDecoder().decode(result.get("plain"));
    }

    //用指定的RSA公钥加密字节数据，并返回Base64字符串(底层工具)
    public String encryptWithPublicKeyToBase64(byte[] plain, String publicKeyBase64)throws  GeneralSecurityException
    {
//        return null;

        return POST("/rsa/encrypt", Map.of(
                "publicKey", publicKeyBase64,
                "plain", Base64.getEncoder().encodeToString(plain)
        )).get("cipher");
    }//加密任意byte[]

    //用当前对象的RSA密钥解密Base64编码的密文(底层工具)
    public byte[] decryptWithPrivateKey(String encryptedBase64)throws GeneralSecurityException
    {
//        return null;

        String plainBase64=POST("rsa/decrypt", Map.of(
                "cipher", encryptedBase64
        )).get("plain");
        return Base64.getDecoder().decode(plainBase64);
    }//解密任意byte[]

    //---------------公钥字符串反序列化工具-----------------//

    //把Base64字符串解析成Java的PublicKey对象
//    public PublicKey parsePublicKey(String pubilcKeyBase64)throws GeneralSecurityException
//    {
//        return null;
//    }
//
//    //把Base64字符串解析成Java的PrivateKey对象
//    public PrivateKey parsePrivateKey(String privKeyBase64)throws GeneralSecurityException
//    {
//        return null;
//    }


    //-----------------2026-04-29新增：客户端本地保存输入的密钥-----------------//

    //返回密钥状态
    public synchronized Map<String, Object> keyStatus() throws GeneralSecurityException
    {
//        return null;

        return Map.copyOf(GET("/key/status"));
    }

    //从文本导入私钥
    public synchronized void importPrivateKeyText(String privateKeyText)
    {
        POST("/key/import-text", Map.of(
                "privateKey", privateKeyText
        ));
    }

    //从文件导入私钥
    public synchronized void importPrivateKeyFile(Path privateKeyPath)
    {
        POST("/key/import-file", Map.of(
                "path", privateKeyPath.toString()
        ));
    }

    //计算当前公钥的SHA-256指纹
    //标识一个公钥，设备身份标识（类似账户的概念）
    public synchronized String publicKeyFingerprint() throws GeneralSecurityException
    {
//        return null;

        return POST("/key/fingerprint", Map.of(
                "publicKey", getEncodedPublicKey()
        )).get("fingerprint");
    }

    //计算指定公钥的SHA-256指纹
    //计算传入的公钥的指纹
    public String publicKeyFingerprint(String publicKeyBase64) throws GeneralSecurityException
    {
//        return null;

        return POST("/key/fingerprint", Map.of(
                "publicKey", publicKeyBase64
        )).get("fingerprint");
    }

    //从本地私钥文件加载密钥对
    private void loadKeyPair(Path privateKeyPath, Path publicKeyPath)//读取私钥文件，解析私钥推导出公钥
    {

    }

    //生成新的RSA密钥对，并保存到文件
    private void generateAndPersistKeyPair(Path privateKeyPath, Path publicKeyPath)
    {

    }

    //导入私钥后保存当前的密钥
    private void persistCurrentKeyPair()
    {

    }

    //把制定的密钥对保存成文件，编码成 PEM 格式。
    private void persistKeyPair(Path privateKeyPath, Path publicKeyPath, KeyPair pair)
    {

    }

    //书写密钥文件
    private void writeKeyFile(Path path, String content, boolean privateFile)
    {

    }

    //设置私钥文件权限,只有文件所有者可以读写
    private void restrictOwnerOnly(Path path)
    {

    }

    //把密钥字节数组编码成PEM格式
    private String encodePem(String type, byte[] encoded)
    {
        return null;
    }


    //清理PEM文本，只保留真正的Base64内容
    private String normalizeKeyText(String keyText)
    {
        return null;
    }

    //根据私钥推导出公钥
    private PublicKey derivePublicKey(PrivateKey privateKey)
    {
        return null;
    }

    //将配置文件中的路径转成真正的绝对路径
    private Path configuredPath(String value)
    {
        return null;
    }

    //把路径开头的 ~ 替换成当前用户的 home 目录。
    private Path expandUserHome(Path path)
    {
        return null;
    }

    //测试用的GET/ POST函数
    private Map<String, String>GET(String path)
    {
        try
        {
            HttpRequest request= HttpRequest.newBuilder()
                    .uri(URI.create(CRYPTO_SERVICE_URL+path))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response.body());
        }
        catch (IOException | InterruptedException e)
        {
            log.info("Crypto service GET failed: "+path);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crypto service GET failed: "+path, e);
        }
    }

    private Map<String, String>POST(String path, Map<String, String> body)
    {
        try
        {
            String json=gson.toJson(body);
            HttpRequest request=HttpRequest.newBuilder()
                    .uri(URI.create(CRYPTO_SERVICE_URL+path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response=httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)
            {
                throw new IllegalStateException("Crypto service POST failed: "+response.body());
            }
            return parse(response.body());
        }
        catch(IOException | InterruptedException e)
        {
            log.info("Crypto service POST failed: "+path);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crypto service POST failed: "+path, e);
        }
    }

    //将加密服务返回的JSON字符串解析成Java的Map<String, String>
    private Map<String, String>parse(String json)
    {
        Type type=new TypeToken<Map<String, String>>(){}.getType();
        return gson.fromJson(json, type);
    }
}
