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

@Service
public class DirectQrCodec
{
    public static final String PREFIX="FST1:";//自定义的一种二维码文本格式,用于IPv6直连握手
    private static final int PROTOCOL_VERSION = 1;
    private final CryptoSupport cryptoSupport;

    public DirectQrCodec(CryptoSupport cryptoSupport) {
        this.cryptoSupport = cryptoSupport;
    }

    public String encodeSenderOffer(SenderOfferQr offer) throws GeneralSecurityException
    {
        Map<String, Object> signed=senderOfferSignedFields(offer, null);
        String signature=sign(signed);
        Map<String, Object>root=senderOfferSignedFields(offer, signature);
        return PREFIX+ Base45.encode(CborLite.encodeCanonical(root));


    }







}
