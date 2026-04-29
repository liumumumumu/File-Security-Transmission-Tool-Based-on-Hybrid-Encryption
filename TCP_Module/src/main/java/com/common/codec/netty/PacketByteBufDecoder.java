package com.common.codec.netty;

import com.common.codec.decoder.ProtocolDecodingLayer.Decoder;
import com.common.codec.decoder.ProtocolDecodingLayer.auth.AuthRequestDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.auth.AuthResponseDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.auth.AuthResultDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.auth.ChallengeDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.file.*;
import com.common.codec.decoder.ProtocolDecodingLayer.heartbeat.PingDecoder;
import com.common.codec.decoder.ProtocolDecodingLayer.heartbeat.PongDecoder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import com.common.protocol.MessageType;

import java.util.List;

/**
 * Author: LQH
 * Date: 2026-04-19
 * Purpose: 把Netty收到的原始二进制数据ByteBuf，按照协议里的消息类型分发给对应的Packet解码器，最终专成上层可用的Java对象；解码分发器
 **/

public class PacketByteBufDecoder extends MessageToMessageDecoder<ByteBuf>
{
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out)    //Netty解码器的核心方法
    {
        //ChannelHandler ctx 当前ChannelHandler的上下文，用来操作Channel，传播时间等
        //ByteBuf msg 收到的原始数据
        //List<Object> out 解码后的对象输出列表，放进去的对象会继续传给下一个handler

        //避免直接操作原始的数据,防止后续解密过程修改原始缓冲区的读指针或内容
        ByteBuf copy=msg.copy();//copy()会创建新的缓冲区，需要手动释放
        try
        {
            byte messageType=copy.getByte(copy.readerIndex());

            //将ByteBuf转成一个具体的协议对象，并放进out
            out.add(resolveDecoder(messageType));//加入out之后，该对象就会继续流向Netty pipline后面的handler
        }
        finally
        {
            //释放前面copy()出来的缓冲区
            copy.release();
        }
    }

    //选择解码器
    private Decoder resolveDecoder(byte messageType)
    {
        return switch (messageType)
        {
            //登陆验证
            case MessageType.Auth_Request ->  new AuthRequestDecoder();
            case MessageType.Challenge ->   new ChallengeDecoder();
            case MessageType.Auth_Response ->  new AuthResponseDecoder();
            case MessageType.Auth_Result ->   new AuthResultDecoder();

            //文件传输
            case MessageType.File_Offer ->   new FileOfferDecoder();
            case MessageType.Device_Selection ->   new DeviceSelectionDecoder();
            case MessageType.File_Accept ->    new FileAcceptDecoder();
            case MessageType.File_Block ->    new FileBlockDecoder();
            case MessageType.ACk ->    new AckDecoder();
            case MessageType.Transfer_Request -> new TransferRequestDecoder();
            case MessageType.Incoming_Transfer_Request -> new IncomingTransferRequestDecoder();
            case MessageType.Receiver_Device_Selection -> new ReceiverDeviceSelectionDecoder();

            //心跳
            case MessageType.Ping ->    new PingDecoder();
            case MessageType.Pong ->    new PongDecoder();

            //未知抛出异常
            default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
        };
    }

}
