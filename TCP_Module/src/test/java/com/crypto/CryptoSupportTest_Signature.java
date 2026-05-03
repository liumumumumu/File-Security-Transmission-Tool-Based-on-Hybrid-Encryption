package com.crypto;

import com.common.config.CryptoKeyProperties;
import com.common.config.CryptoServiceProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CryptoSupportTest_Signature {

    private final CryptoSupport cryptoSupport;

    public CryptoSupportTest_Signature(CryptoSupport cryptoSupport) {
        this.cryptoSupport = cryptoSupport;
    }

    private void testSignatureVerifySuccess() {
        try {
            String challenge = "test-challenge-123";
            String publicKey = cryptoSupport.getEncodedPublicKey();

            String signature = cryptoSupport.signToBase64(challenge);
            boolean verified = cryptoSupport.verifySignature(publicKey, challenge, signature);

            log.info("签名验证成功场景: {}", verified);

            if (!verified) {
                throw new IllegalStateException("签名验证应该成功，但实际失败");
            }
        } catch (Exception e) {
            log.info("签名验证成功场景测试失败: {}", e.getMessage());
        }
    }

    private void testSignatureVerifyFailWhenChallengeChanged() {
        try {
            String challenge = "test-challenge-123";
            String tamperedChallenge = "test-challenge-456";
            String publicKey = cryptoSupport.getEncodedPublicKey();

            String signature = cryptoSupport.signToBase64(challenge);
            boolean verified = cryptoSupport.verifySignature(publicKey, tamperedChallenge, signature);

            log.info("Challenge 被篡改场景: {}", verified);

            if (verified) {
                throw new IllegalStateException("Challenge 被篡改后验签应该失败，但实际成功");
            }
        } catch (Exception e) {
            log.info("Challenge 被篡改场景测试失败: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        CryptoKeyProperties cryptoKeyProperties = new CryptoKeyProperties();

        CryptoServiceProperties cryptoServiceProperties = new CryptoServiceProperties();
        cryptoServiceProperties.setAddress("127.0.0.1");
        cryptoServiceProperties.setPort(9080);

        CryptoSupport cryptoSupport = new CryptoSupport(cryptoKeyProperties, cryptoServiceProperties);

        // 手动创建对象时，@PostConstruct 不会自动执行，所以这里必须手动调用
        cryptoSupport.init();

        CryptoSupportTest_Signature test =
                new CryptoSupportTest_Signature(cryptoSupport);

        test.testSignatureVerifySuccess();
        test.testSignatureVerifyFailWhenChallengeChanged();
    }
}
