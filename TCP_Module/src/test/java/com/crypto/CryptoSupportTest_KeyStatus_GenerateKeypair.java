package com.crypto;


import com.common.config.CryptoServiceProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CryptoSupportTest_KeyStatus_GenerateKeypair {

    private CryptoSupport cryptoSupport;//加密解密相关函数

    public CryptoSupportTest_KeyStatus_GenerateKeypair(CryptoSupport cryptoSupport) {
        this.cryptoSupport = cryptoSupport;
    }

    //测试密钥状态
    private void TestKeyStatus()
    {
        try
        {
            log.info("Key Status: "+cryptoSupport.keyStatus().toString());
        }
        catch (Exception e)
        {
            log.info("Error occured while testing key statues: "+e.getMessage());
        }
    }

    private void TestKeyPairDelete()
    {
        cryptoSupport.deleteLocalKeyPair();
    }

    //测试生成密钥
    private void GenerateKeyTest()
    {
        TestKeyStatus();//生成前
        cryptoSupport.generateKeyPair();
        TestKeyStatus();//生成后

        log.info("New private key: "+cryptoSupport.getEncodedPrivateKey().toString());
        log.info("New public key: "+cryptoSupport.getEncodedPublicKey().toString());
    }

    public static void main(String[] args)
    {
        CryptoServiceProperties  cryptoServiceProperties=new CryptoServiceProperties();
        CryptoSupport cryptoSupport=new CryptoSupport(cryptoServiceProperties);
        cryptoSupport.setCRYPTO_SERVICE_URL("http://127.0.0.1:9080");
        log.info(cryptoSupport.getCryptoServiceUrl());
        CryptoSupportTest_KeyStatus_GenerateKeypair test = new CryptoSupportTest_KeyStatus_GenerateKeypair(cryptoSupport);

        test.TestKeyPairDelete();
        test.TestKeyStatus();
        test.GenerateKeyTest();
    }
}
