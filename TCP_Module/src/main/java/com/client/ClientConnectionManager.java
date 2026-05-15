package com.client;


import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.protocol.Packet;
import com.common.protocol.auth.AuthRequestPacket;
import com.common.protocol.auth.AuthResponsePacket;
import com.common.protocol.auth.AuthResultPacket;
import com.common.protocol.auth.ChallengePacket;
import com.crypto.CryptoSupport;
import com.common.service.PushNotificationService;
import com.session.ClientConnectionStatus;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Author: LQH
 * Date: 2026-04-23
 * Purpose: 客户端的连接与认证管理器。负责连接远端TCP服务器，
 * 完成身份认证，维护连接状态，并推送连接事件
 * 客户端Netty连接的启动和管理类(真正的调用Netty启动连接)
 **/

@Service
public class ClientConnectionManager
{
    private final ClientProperties clientProperties;
    private final NodeProperties nodeProperties;
    private final CryptoSupport cryptoSupport;
    private final ClientChannelInitializer clientChannelInitializer;//配置客户端Channel的pipeline
    private final PushNotificationService pushNotificationService;


    //NioEventLoopGroup就是指 BossEventLoop, WorkerEventLoop(selector, thread);
    private final EventLoopGroup workerGroup=new NioEventLoopGroup();

    //volatile的核心作用;1.1. 保证可见性,一个线程改了值，其他线程马上能看到。2.禁止某些指令重排序
    //这些字段会被不同线程同时读写
    private volatile Channel channel;
    private volatile String connectedHost;
    private volatile int connectedPort;
    private volatile ClientConnectionStatus status = ClientConnectionStatus.DISCONNECTED;

    //CompletableFuture是异步编程类
    private volatile CompletableFuture<Void> authFuture;//认证结果的异步凭证
    //不需要返回对象，只需要知道认证是成功/ 失败

    public ClientConnectionManager(
            ClientProperties clientProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            ClientChannelInitializer clientChannelInitializer,
            PushNotificationService pushNotificationService
    )
    {
        this.clientProperties = clientProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.clientChannelInitializer = clientChannelInitializer;
        this.pushNotificationService = pushNotificationService;
    }

    //同一时刻只能有一个线程进入这些方法
    //确保多个线程之间对这个变量的最新值可见
    public synchronized CompletableFuture<Void> connectAndAuthenticate(String host, int port)//连接指定服务端，并完成客户端身份认证
    {
        //避免重复连接，认证同一个目标服务端
        if(isAuthenticated() && host.equals(connectedHost) && port == connectedPort)
        {
            return CompletableFuture.completedFuture(null);
        }

        disconnect();//确保同一时间只维护一条客户端连接
        status = ClientConnectionStatus.CONNECTING;
        pushNotificationService.publish("client-connecting", Map.of(
                "host", host,
                "port", port
        ));

        //配置启动类
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)//添加EventLoop
                .channel(NioSocketChannel.class)//选择客户端channel实现
                //设置客户端连接服务端超时的时间（毫秒）；当bootstrap.connect时没有在指定的时间内建立成功的连接，就视为连接失败
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientProperties.getConnectionTimeout())
                //添加处理器
                .handler(clientChannelInitializer);//每当创建一个新的Channel时Netty就调用这个initializer去初始化连接的处理链

        CompletableFuture<Void> future = new CompletableFuture<>();
        authFuture = future;
        //异步连接服务端，并将认证结果绑定到CompletableFuture上
        bootstrap.connect(host, port).addListener(connectFuture->{
            //连接失败
            if(!connectFuture.isSuccess())
            {
                status = ClientConnectionStatus.DISCONNECTED;
                future.completeExceptionally(connectFuture.cause());
                pushNotificationService.publish("client-connect-failed", Map.of(
                        "message", connectFuture.cause().getMessage()
                ));
                return ;
            }

            //保存连接信息
            connectedHost = host;
            connectedPort = port;
            channel = ((io.netty.channel.ChannelFuture) connectFuture).channel();//从Netty的连接结果取出真正的Channel,通信通道
            status=ClientConnectionStatus.AUTHENTICATING;
            channel.writeAndFlush(new AuthRequestPacket(nodeProperties.getDeviceId(), cryptoSupport.getEncodedPublicKey()));//拿到通信通道之后，后续就可以通过它发送数据了
        });
        return future.orTimeout(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
    }

    public synchronized void send(Packet packet)
    {
        if(channel == null || !channel.isActive())
        {
            throw new IllegalStateException("Client is not connected");
        }
        channel.writeAndFlush(packet);
    }

    public synchronized void disconnect()
    {
        if(channel != null)
        {
            channel.close();
            channel = null;
        }
        status = ClientConnectionStatus.DISCONNECTED;
    }

    //收到服务端的challenge后，生成签名并发回服务端
    //由Netty pipeline里的客户端入站Handler调用
    public void handleChallenge(ChallengePacket packet)throws GeneralSecurityException
    {
        send(
                new AuthResponsePacket(
                        packet.getChallengeId(),
                        cryptoSupport.getEncodedPublicKey(),
                        cryptoSupport.signToBase64(packet.getChallenge())
                )
        );
    }


    //处理服务端返回的认证结果
    //由Netty管理
    public void handleAuthResult(AuthResultPacket packet)
    {
        if(packet.isSuccess())
        {
            status=ClientConnectionStatus.AUTHENTICATED;
            if(authFuture!=null && !authFuture.isDone())
            {
                authFuture.complete(null);
            }
            //通知认证成功事件
            pushNotificationService.publish("client-authenticated",
                    //事件内容
                    Map.of(
                    "deviceId", nodeProperties.getDeviceId(),
                    "host", connectedHost,
                    "port", connectedPort
            ));
        }
    }

    //处理连接断开事件
    //由Netty管理
    public void handleChannelInactive()
    {
        status=ClientConnectionStatus.DISCONNECTED;
        if(authFuture!=null && !authFuture.isDone())
        {
            //连接或认证流程还没完成连接就断开了的情况
            authFuture.completeExceptionally(new IllegalStateException("Connection closed"));
        }
        pushNotificationService.publish("client-disconnected",
                //内容
                Map.of(
                        "deviceId", nodeProperties.getDeviceId()
                ));
    }

    public boolean isAuthenticated()
    {
        return status == ClientConnectionStatus.AUTHENTICATED && channel!=null && channel.isActive();
    }

    //返回客户端当前连接状态信息
    public Map<String, Object> currentStatus()
    {
        try {
            Map<String, Object> keyStatus = cryptoSupport.keyStatus();
            String accountId = isTruthy(keyStatus.get("hasPrivateKey")) ? cryptoSupport.publicKeyFingerprint() : "";
            return Map.of(
                    "deviceId", nodeProperties.getDeviceId(),
                    "accountId", accountId,
                    "status", status.name(),
                    "connectedHost", connectedHost == null ? "" : connectedHost,
                    "connectedPort", connectedPort
            );
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to calculate accountId", ex);
        }
    }

    private boolean isTruthy(Object value)
    {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    //获取本地的公钥
    public String getLocalPublicKey()
    {
        return cryptoSupport.getEncodedPublicKey();
    }

    //在对象被销毁之前，先自动调用这个方法
    @PreDestroy
    public void shutdown()
    {
        disconnect();
        //关闭Netty的事件循环线程组
        workerGroup.shutdownGracefully();
    }
}
