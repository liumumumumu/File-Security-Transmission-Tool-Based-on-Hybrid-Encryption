package com.client.direct;


import com.client.direct.qr.ReceiverResponseQr;
import com.client.direct.qr.QrArtifactService;
import com.client.direct.qr.SenderOfferQr;
import com.client.transport.DirectPeerTransport;
import com.client.transport.PacketTransport;
import com.common.config.NodeProperties;
import com.common.protocol.direct.DirectSessionAcceptedPacket;
import com.common.protocol.direct.DirectSessionChallengePacket;
import com.common.protocol.direct.DirectSessionHelloPacket;
import com.common.protocol.direct.DirectSessionProofPacket;
import com.crypto.CryptoSupport;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Author: LQH
 * Date: 2026-05-21
 * Purpose: 连接管理器，负责将两个客户端连接起来；认证通过后将这条Netty连接包装成PacketTransport交给传输业务层发文件
 * 1.扫描本机公网IPv6地址
 * 2.接收方启动直连监听端口
 * 3.发送方主动连接接收方IPv6地址和端口
 * 4.完成直连握手，处理直连握手包(DirectSessionHelloPacket, DirectSessionChallengePacket, DirectSessionProofPacket, DirectSessionAcceptedPacket)
 * 5.将Netty Channel 转成文件传输层能用的Transport
 *
 * */


@Service
public class DirectPeerConnectionManager
{
    private final DirectPeerChannelInitializer channelInitializer;//Netty通道初始化类
    private final CryptoSupport cryptoSupport;//加密服务
    private final NodeProperties nodeProperties;//节点信息
    private final QrArtifactService qrArtifactService;//二维码文件管理服务
    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);//处理接收连接
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();//处理连接读写
    private final Map<Channel, DirectPeerTransport> transportsByChannel = new ConcurrentHashMap<>();//保存已经认证成功的直连通道
    private final Map<Channel, DirectPeerSession> sessionsByChannel = new ConcurrentHashMap<>();
    private final Map<String, PendingReceiver> pendingReceivers = new ConcurrentHashMap<>();//接收方等待中的邀请
    private final Map<String, PendingSender> pendingSenders = new ConcurrentHashMap<>();//发送方等待中的连接
    private final Map<String, ChallengeState> challenges = new ConcurrentHashMap<>();//接收方发出的挑战

    private volatile Channel listenerChannel;//当前接收方正在监听的Netty服务端通道

    public DirectPeerConnectionManager(DirectPeerChannelInitializer channelInitializer, CryptoSupport cryptoSupport, NodeProperties nodeProperties, QrArtifactService qrArtifactService)
    {
        this.channelInitializer = channelInitializer;
        this.cryptoSupport = cryptoSupport;
        this.nodeProperties = nodeProperties;
        this.qrArtifactService = qrArtifactService;
    }

    //扫描本机的网卡，找出可用于公网直连的IPv6地址
    public List<String> publicGlobalIpv6Addresses()
    {
        try
        {
            List<String> addresses = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();//遍历网卡
            while(interfaces.hasMoreElements())
            {
                NetworkInterface networkInterface = interfaces.nextElement();
                if(!networkInterface.isUp() || networkInterface.isLoopback())//过滤掉无法用于直连通信的网卡
                {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                //遍历网卡上的IP地址，只保留IPv6地址
                while(inetAddresses.hasMoreElements())
                {
                    InetAddress address = inetAddresses.nextElement();
                    if(address instanceof Inet6Address inet6Address && isGlobalUnicast(inet6Address))
                    {
                        addresses.add(inet6Address.getHostAddress().split("%")[0]);
                    }
                }
            }
            return addresses.stream().distinct().toList();
        }
        catch(Exception ex)
        {
            throw new IllegalStateException("Unable to inspect IPv6 addresses", ex);
        }
    }

    //开始接收
    public int startReceiver(SenderOfferQr senderOffer, int preferredPort) throws InterruptedException
    {
        stopListener();//先关闭之前可能存在的监听(目前只允许一个直连监听)
        int port = preferredPort <= 0 ? 0 : preferredPort;//如果传入端口小于等于 0，就让系统随机分配可用端口，如果传入固定端口，就用指定端口
        ServerBootstrap bootstrap = new ServerBootstrap();

        //启动一个IPv6 TCP监听
        listenerChannel = bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(channelInitializer)
                .bind(new InetSocketAddress("::", port))
                .sync()
                .channel();
        int actualPort = ((InetSocketAddress) listenerChannel.localAddress()).getPort();//拿到实际监听的端口
        pendingReceivers.put(senderOffer.getInviteId(), new PendingReceiver(senderOffer, CompletableFuture.completedFuture(null)));//保存这个直连邀请，等待发送方连接
        return actualPort;
    }

    //发送方根据接收方二维码里的IPv6地址和端口，主动连接接收方并收起直连握手
    public CompletableFuture<DirectSessionInfo> connectAsSender(SenderOfferQr senderOffer, ReceiverResponseQr response, int timeoutSeconds)
    {
        CompletableFuture<DirectSessionInfo> future = new CompletableFuture<>();//异步结果
        String sessionId = UUID.randomUUID().toString();//生成本次直连会话的sessionId
        pendingSenders.put(sessionId, new PendingSender(senderOffer, response, future));//将当前发送方会话暂存，然后等待后续握手结果

        Bootstrap bootstrap = new Bootstrap();//创建Netty客户端
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .handler(channelInitializer);

        //读取二维码中的信息
        String host = response.getIpv6AddressCandidates().get(0);//读取IPv6地址和端口
        bootstrap.connect(host, response.getPort()).addListener(connectFuture -> {
            if(!connectFuture.isSuccess())//连接失败的情况
            {
                pendingSenders.remove(sessionId);//清理等待状态
                future.completeExceptionally(connectFuture.cause());//让future对象失败
                return;
            }
            //连接成功
            Channel channel = ((io.netty.channel.ChannelFuture) connectFuture).channel();
            try
            {
                //如果连接成功就发送DirectSessionHelloPacket
                String signature = cryptoSupport.signToBase64(helloData(senderOffer.getInviteId(), sessionId, response.getConnectionNonce()));//发送方使用自己的私钥对这段数据进行签名
                channel.writeAndFlush(new DirectSessionHelloPacket(
                        senderOffer.getInviteId(),
                        sessionId,
                        senderOffer.getSenderAccountId(),
                        senderOffer.getSenderDeviceId(),
                        senderOffer.getSenderPublicKey(),
                        response.getConnectionNonce(),
                        signature
                ));
            }
            catch(Exception ex)
            {
                pendingSenders.remove(sessionId);
                future.completeExceptionally(ex);
                channel.close();
            }
        });
        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS);//返回一个future对象，等待接收方验证通过
    }

    //处理第一个直连握手数据包DirectSessionHelloPacket的函数
    public void handleHello(Channel channel, DirectSessionHelloPacket packet) throws GeneralSecurityException
    {
        PendingReceiver pending = pendingReceivers.get(packet.getInviteId());//根据inviteId查找之前保存的等待邀请
        if(pending == null || pending.senderOffer().getExpiresAt().isBefore(Instant.now()))//inviteId不存在或者邀请已经过期的情况，拒绝连接
        {
            channel.writeAndFlush(new DirectSessionAcceptedPacket(packet.getInviteId(),
                    packet.getSessionId(),
                    false,
                    "Invite expired or not found",
                    "",
                    "",
                    ""));
            channel.close();//关闭TCP连接
            return;
        }
        SenderOfferQr expected = pending.senderOffer();//取出接收方之前从发送方二维码里读到的原始信息

        //检验发送方发来的身份信息是否和二维码里的内容一致
        if(!expected.getSenderAccountId().equals(packet.getSenderAccountId())
                || !expected.getSenderDeviceId().equals(packet.getSenderDeviceId())
                || !expected.getSenderPublicKey().equals(packet.getSenderPublicKey())
                || !cryptoSupport.verifySignature(packet.getSenderPublicKey(), helloData(packet.getInviteId(), packet.getSessionId(), packet.getConnectionNonce()), packet.getSignature()))//防止使用相同的inviteId,但是换成自己的账号，公钥来连接
        {
            //身份不匹配或者签名错误
            channel.writeAndFlush(new DirectSessionAcceptedPacket(packet.getInviteId(),
                    packet.getSessionId(),
                    false,
                    "Sender proof failed",
                    "",
                    "",
                    ""));
            channel.close();//拒绝连接并关闭TCP
            return;
        }
        //连接通过就生成一个随机Challenge
        String challenge = UUID.randomUUID().toString();
        challenges.put(packet.getSessionId(), new ChallengeState(packet.getInviteId(), packet.getSessionId(), packet.getSenderAccountId(), packet.getSenderDeviceId(), packet.getSenderPublicKey(), challenge));//保存challenge状态
        channel.writeAndFlush(new DirectSessionChallengePacket(packet.getInviteId(), packet.getSessionId(), challenge));//发回challenge
    }

    //发送方收到接收方发来的挑战包DirectSessionChallengePacket后的处理函数
    public void handleChallenge(Channel channel, DirectSessionChallengePacket packet) throws GeneralSecurityException
    {
        PendingSender pending = pendingSenders.get(packet.getSessionId());//发送方使用sessionId去pendingSenders找当前等待中的直连会话连接
        if(pending == null)//sessionId不存在或者已经过期
        {
            channel.close();
            return;
        }

        //发送证明包DirectSessionProofPacket
        channel.writeAndFlush(new DirectSessionProofPacket(
                packet.getInviteId(),
                packet.getSessionId(),
                cryptoSupport.signToBase64(packet.getChallenge())//签名challenge
        ));
    }

    //接收方收到发送方DirectSessionProofPacket后的处理函数
    public void handleProof(Channel channel, DirectSessionProofPacket packet) throws GeneralSecurityException
    {
        ChallengeState challenge = challenges.remove(packet.getSessionId());//取出之前保存的challenge
        if(challenge == null || !cryptoSupport.verifySignature(challenge.senderPublicKey(), challenge.challenge(), packet.getSignature()))//判断challenge是否存在以及签名验证是否通过
        {
            channel.writeAndFlush(new DirectSessionAcceptedPacket(packet.getInviteId(),
                    packet.getSessionId(),
                    false,
                    "Challenge proof failed",
                    "",
                    "",
                    ""));
            channel.close();
            return;
        }

        //创建直连transport
        DirectPeerTransport transport = new DirectPeerTransport(channel, challenge.senderDeviceId());
        transportsByChannel.put(channel, transport);//登记这个transport
        sessionsByChannel.put(channel, new DirectPeerSession(
                challenge.senderAccountId(),
                challenge.senderDeviceId(),
                challenge.senderPublicKey(),
                transport
        ));

        pendingReceivers.remove(packet.getInviteId());//清理连接状态
        qrArtifactService.deleteInvite(packet.getInviteId());//删除本地二维码文件
        if(listenerChannel != null)//关闭监听端口
        {
            listenerChannel.close();
            listenerChannel = null;
        }

        //通知发送方连接成功
        channel.writeAndFlush(new DirectSessionAcceptedPacket(
                packet.getInviteId(),
                packet.getSessionId(),
                true,
                "Direct session accepted",
                cryptoSupport.publicKeyFingerprint(),
                nodeProperties.getDeviceId(),
                cryptoSupport.getEncodedPublicKey()
        ));
    }

    //发送方收到接收方最终确认包DirectSessionAcceptedPacket后的处理函数
    public void handleAccepted(Channel channel, DirectSessionAcceptedPacket packet)
    {
        PendingSender pending = pendingSenders.remove(packet.getSessionId());//根据sessionId找到之前保存的等待状态
        if(pending == null)
        {
            return;
        }
        if(!packet.isAccepted())
        {
            pending.future().completeExceptionally(new IllegalStateException(packet.getMessage()));
            channel.close();
            return;
        }
        //接收方接受连接，则创建直连transport
        DirectPeerTransport transport = new DirectPeerTransport(channel, packet.getReceiverDeviceId());
        transportsByChannel.put(channel, transport);//将通道登记起来
        sessionsByChannel.put(channel, new DirectPeerSession(
                packet.getReceiverAccountId(),
                packet.getReceiverDeviceId(),
                packet.getReceiverPublicKey(),
                transport
        ));


        //完成future
        pending.future().complete(new DirectSessionInfo(
                packet.getInviteId(),
                packet.getSessionId(),
                packet.getReceiverAccountId(),
                packet.getReceiverDeviceId(),
                packet.getReceiverPublicKey(),
                transport,
                pending.response()
        ));
    }

    //根据当前Netty Channel查找对应的直连传输对象PacketTransport
    public Optional<PacketTransport> transportFor(Channel channel)
    {
        return Optional.ofNullable(transportsByChannel.get(channel));
    }

    public List<DirectPeerSession> activeSessions()
    {
        return sessionsByChannel.values().stream()
                .filter(session -> session.transport().isActive())
                .toList();
    }

    public Optional<DirectPeerSession> activeSessionForAccount(String peerAccountId)
    {
        if(peerAccountId == null || peerAccountId.isBlank())
        {
            return Optional.empty();
        }
        return activeSessions().stream()
                .filter(session -> peerAccountId.equals(session.peerAccountId()))
                .findFirst();
    }

    public Optional<DirectPeerSession> singleActiveSession()
    {
        List<DirectPeerSession> activeSessions = activeSessions();
        return activeSessions.size() == 1 ? Optional.of(activeSessions.get(0)) : Optional.empty();
    }

    public int activeSessionCount()
    {
        return activeSessions().size();
    }


    //处理断开连接的情况，当某条直连TCP连接断开时，从transportByChannel里删除对应的transport记录
    public void handleChannelInactive(Channel channel)
    {
        transportsByChannel.remove(channel);

    }

    //停止接收方正在监听的IPv6直连端口，并清空等待中的接收方邀请状态
    public void stopListener()
    {
        if(listenerChannel != null)//判断是否有正在监听的服务端通道
        {
            listenerChannel.close();
            listenerChannel = null;
        }
        pendingReceivers.clear();
    }

    @PreDestroy
    public void shutdown()
    {
        stopListener();//关闭当前IPv6监听端口，并清空等待中的接收方邀请
        workerGroup.shutdownGracefully();//关闭Netty线程组
        bossGroup.shutdownGracefully();
    }

    //判断IPv6是否是全网全局单播地址
    private boolean isGlobalUnicast(Inet6Address address)
    {
        byte[] bytes = address.getAddress();//16字节二进制形式的IPv6地址
        int first = bytes[0] & 0xff;
        return first >= 0x20 && first <= 0x3f       //地址必须在 IPv6 全局单播地址范围2000::/3
                && !address.isAnyLocalAddress()
                && !address.isLoopbackAddress()
                && !address.isLinkLocalAddress()
                && !address.isMulticastAddress();
    }

    //将本次直连握手需要的签名/验签的关键数据拼成一段固定的字符串，并做Base64编码
    private String helloData(String inviteId, String sessionId, String connectionNonce)
    {
        return Base64.getEncoder().encodeToString((inviteId + "|" + sessionId + "|" + connectionNonce).getBytes());
    }

    //用于保存发送方的二维码信息
    private record PendingReceiver(SenderOfferQr senderOffer,
                                   CompletableFuture<Void> future) {
    }

    //用于存储发送方发起直连后等待接收方的最终确认信息
    private record PendingSender(SenderOfferQr senderOffer,
                                 ReceiverResponseQr response,
                                 CompletableFuture<DirectSessionInfo> future) {
    }

    //接收方发给发送方的Challenge,以及验证这个Challenge所需要的信息
    private record ChallengeState(String inviteId,
                                  String sessionId,
                                  String senderAccountId,
                                  String senderDeviceId,
                                  String senderPublicKey,
                                  String challenge) {
    }

    public record DirectPeerSession(String peerAccountId,
                                    String peerDeviceId,
                                    String peerPublicKey,
                                    PacketTransport transport) {
    }

}
