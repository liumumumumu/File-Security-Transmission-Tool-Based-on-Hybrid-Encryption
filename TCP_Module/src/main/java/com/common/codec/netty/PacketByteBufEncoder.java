package com.common.codec.netty;


import com.common.codec.encoder.Encoder;
import com.common.codec.encoder.auth.AuthRequestEncoder;
import com.common.codec.encoder.auth.AuthResponseEncoder;
import com.common.codec.encoder.auth.AuthResultEncoder;
import com.common.codec.encoder.auth.ChallengeEncoder;
import com.common.codec.encoder.file.*;
import com.common.codec.encoder.heartbeat.PingEncoder;
import com.common.codec.encoder.heartbeat.PongEncoder;
import com.common.codec.encoder.searchUser.OnlineUserSearchRequestEncoder;
import com.common.codec.encoder.searchUser.OnlineUserSearchResultEncoder;
import com.common.protocol.Packet;
import com.common.protocol.auth.AuthRequestPacket;
import com.common.protocol.auth.AuthResponsePacket;
import com.common.protocol.auth.AuthResultPacket;
import com.common.protocol.auth.ChallengePacket;
import com.common.protocol.file.*;
import com.common.protocol.heartbeat.PingPacket;
import com.common.protocol.heartbeat.PongPacket;
import com.common.protocol.searchUser.OnlineUserSearchRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: 统一协议编码器，负责把程序里面的Packet对象编码成可以在Netty网络中发送的二进制数据
 **/

public class PacketByteBufEncoder extends MessageToMessageEncoder<Packet>
{
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, List<Object> out)
    {
        //编码后的结果放进out列表，Netty会继续处理out里的对象，并发送到网络中
        out.add(resolveEncoder(msg).encode(msg));
    }

    private Encoder resolveEncoder(Packet packet)
    {
        //登陆校验
        if(packet instanceof AuthRequestPacket)
        {
            return new AuthRequestEncoder();
        }
        else if(packet instanceof ChallengePacket)
        {
            return new ChallengeEncoder();
        }
        else if(packet instanceof AuthResponsePacket)
        {
            return new AuthResponseEncoder();
        }
        else if(packet instanceof AuthResultPacket)
        {
            return new AuthResultEncoder();
        }
        //文件传输
        else if(packet instanceof FileOfferPacket)
        {
            return new FileOfferEncoder();
        }
        else if(packet instanceof DeviceSelectionPacket)
        {
            return new DeviceSelectionEncoder();
        }
        else if(packet instanceof FileAcceptPacket)
        {
            return new FileAcceptEncoder();
        }
        else if(packet instanceof FileBlockPacket)
        {
            return new FileBlockEncoder();
        }
        else if(packet instanceof AckPacket)
        {
            return new AckEncoder();
        }
        if (packet instanceof TransferRequestPacket) {
            return new TransferRequestEncoder();
        }
        if (packet instanceof IncomingTransferRequestPacket) {
            return new IncomingTransferRequestEncoder();
        }
        if (packet instanceof ReceiverDeviceSelectionPacket) {
            return new ReceiverDeviceSelectionEncoder();
        }
        if (packet instanceof OnlineUserSearchRequestPacket) {
            return new OnlineUserSearchRequestEncoder();
        }
        if (packet instanceof OnlineUserSearchResultPacket) {
            return new OnlineUserSearchResultEncoder();
        }
        if (packet instanceof TransferCancelPacket) {
            return new TransferCancelEncoder();
        }
        if (packet instanceof TransferCancelAckPacket) {
            return new TransferCancelAckEncoder();
        }
        if (packet instanceof RetransmitRequestPacket) {
            return new RetransmitRequestEncoder();
        }
        if (packet instanceof RetransmitAckPacket) {
            return new RetransmitAckEncoder();
        }
        //心跳
        else if(packet instanceof PingPacket)
        {
            return new PingEncoder();
        }
        else if(packet instanceof PongPacket)
        {
            return new PongEncoder();
        }
        //未知类型
        else
        {
            throw new IllegalArgumentException("Unsupported packet type: " + packet.getClass().getName());
        }
    }
}
