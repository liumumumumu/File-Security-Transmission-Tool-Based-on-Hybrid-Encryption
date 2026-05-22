package com.client.direct;

import com.client.direct.qr.DirectQrCodec;
import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.QrArtifactService;
import com.client.direct.qr.ReceiverResponseQr;
import com.client.direct.qr.SenderOfferQr;
import com.common.config.NodeProperties;
import com.crypto.CryptoSupport;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 直连握手服务的业务编排服务
 * 1.生成发送方邀请
 * 2.校验二维码是否过期
 * 3.获取接收方IPv6地址
 * 4.根据端口配置启动接收方监听
 * 5.生成接收方响应
 * 6.校验响应是否匹配当前邀请
 * 7.触发发送方建立直连
 * 8.管理握手二维码二年级的读取，清理和删除
 *
 * */

@Service
public class DirectHandshakeService
{
    private static final Duration QR_TTL = Duration.ofMinutes(15);//二维码的有效期是15分钟
    private final DirectQrCodec directQrCodec;//二维码文本的编码和解码器
    private final QrArtifactService qrArtifactService;//生成二维码文件
    private final DirectPeerConnectionManager directPeerConnectionManager;//真正负责直连网络连接的工具
    private final DirectSettingsService directSettingsService;//读取直连配置
    private final CryptoSupport cryptoSupport;
    private final NodeProperties nodeProperties;//提供本机节点配置

    public DirectHandshakeService(DirectQrCodec directQrCodec,
                                  QrArtifactService qrArtifactService,
                                  DirectPeerConnectionManager directPeerConnectionManager,
                                  DirectSettingsService directSettingsService,
                                  CryptoSupport cryptoSupport,
                                  NodeProperties nodeProperties)
    {
        this.directQrCodec = directQrCodec;
        this.qrArtifactService = qrArtifactService;
        this.directPeerConnectionManager = directPeerConnectionManager;
        this.directSettingsService = directSettingsService;
        this.cryptoSupport = cryptoSupport;
        this.nodeProperties = nodeProperties;
    }

    //发送方发出的直连邀请
    public SenderHandshakeOffer createSenderOffer() throws GeneralSecurityException
    {
        String publicKey = cryptoSupport.getEncodedPublicKey();

        //创建发送方邀请对象
        SenderOfferQr offer = new SenderOfferQr(
                UUID.randomUUID().toString(),   //随机生成的邀请ID
                cryptoSupport.publicKeyFingerprint(publicKey),  //发送方账号(公钥指纹)
                nodeProperties.getDeviceId(),   //发送方设备ID
                publicKey,  //发送方公钥
                Instant.now().plus(QR_TTL),     //过期时间
                ""
        );
        String text = directQrCodec.encodeSenderOffer(offer);//编码成FST1文本

        //写入本地二维码相关文件
        QrArtifact artifact = qrArtifactService.writeArtifacts("sender", offer.getInviteId(), offer.getExpiresAt(), text);
        return new SenderHandshakeOffer(offer, text, artifact);
    }

    //接收方生成的响应
    public ReceiverListeningSession createReceiverResponse(String senderFst1Text) throws Exception
    {
        SenderOfferQr senderOffer = directQrCodec.decodeSenderOffer(senderFst1Text);//解码发送
        if(senderOffer.getExpiresAt().isBefore(Instant.now()))//检查二维码是否过期
        {
            throw new IllegalStateException("Sender QR has expired");
        }
        List<String> addresses = directPeerConnectionManager.publicGlobalIpv6Addresses();//获取本机公网IPv6地址
        if(addresses.isEmpty())//判断是否有可用的公网IPv6地址
        {
            throw new IllegalStateException("The current network does not support direct IPv6 connectivity.");
        }

        DirectSettings settings = directSettingsService.current();//读取端口设置
        int preferredPort = settings.getListenPortMode() == DirectListenPortMode.FIXED ? settings.getFixedListenPort() : 0;//根据端口模式决定监听端口
        int port = directPeerConnectionManager.startReceiver(senderOffer, preferredPort);//启动接收方监听

        //生成接收方响应对象
        String publicKey = cryptoSupport.getEncodedPublicKey();
        ReceiverResponseQr response = new ReceiverResponseQr(
                senderOffer.getInviteId(),//同一个inviteId
                cryptoSupport.publicKeyFingerprint(publicKey),//接收方账号指纹
                nodeProperties.getDeviceId(),//接收方设备ID
                publicKey,//接收方公钥
                Instant.now().plus(QR_TTL),//响应过期时间
                addresses,//接收方公网IPv6地址列表
                port,//接收方实际监听端口
                UUID.randomUUID().toString(),//随机connectionNonce
                ""
        );

        //给接收方的响应内容进行编码
        String text = directQrCodec.encodeReceiverResponse(response, senderOffer.getSenderPublicKey());

        //写入二维码文件
        QrArtifact artifact = qrArtifactService.writeArtifacts("receiver", response.getInviteId(), response.getExpiresAt(), text);
        return new ReceiverListeningSession(response, artifact, port);
    }

    //发送方连接接收方的函数
    public CompletableFuture<DirectSessionInfo> connectSender(SenderOfferQr offer, String receiverFst1Text)
            throws GeneralSecurityException
    {
        ReceiverResponseQr response = directQrCodec.decodeReceiverResponse(receiverFst1Text);//解码接收方响应信息
        if(!offer.getInviteId().equals(response.getInviteId()))//检查inviteId是否匹配
        {
            throw new IllegalArgumentException("Receiver QR does not match sender invite");
        }
        if(response.getExpiresAt().isBefore(Instant.now()))//检查接收方响应是否过期
        {
            throw new IllegalStateException("Receiver QR has expired");
        }

        //由连接管理器发起连接
        return directPeerConnectionManager.connectAsSender(offer, response, 30);
    }

    //清理过期二维码文件
    public int cleanupExpiredQr()
    {
        return qrArtifactService.cleanupExpired();
    }

    //删除指定邀请ID对应的二维码文件
    public void deleteQrInvite(String inviteId)
    {
        qrArtifactService.deleteInvite(inviteId);
    }

    //读取二维码，支持FST1文本，也支持文件路径
    public String readFst1Text(String valueOrPath)
    {
        return qrArtifactService.readFst1Text(valueOrPath);
    }

    //临时DTO对象，用来保存结构化邀请对象，编码后的二维码文本，生成的文本信息
    public record SenderHandshakeOffer(SenderOfferQr offer,
                                       String text,
                                       QrArtifact artifact) {
    }
}
