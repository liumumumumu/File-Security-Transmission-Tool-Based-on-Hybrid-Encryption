package com.client.direct;

import com.client.direct.qr.DirectQrCodec;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DirectHandshakeService
{
    private static final Duration QR_TTL = Duration.ofMinutes(15);//每个QR code的过期时间
//    private final DirectQrCodec directQrCodec;



}
