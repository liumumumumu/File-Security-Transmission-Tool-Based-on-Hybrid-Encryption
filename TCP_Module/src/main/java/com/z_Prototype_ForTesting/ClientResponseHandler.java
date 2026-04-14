package com.z_Prototype_ForTesting;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 客户端用于处理服务端响应的处理器。
 *
 * 因为前面已经通过 StringDecoder 把服务端返回的数据解码成了 String，
 * 所以这里直接处理字符串即可，不需要再手动处理 ByteBuf。
 */
public class ClientResponseHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        // 服务端当前只会返回类似 "SUCCESS\n" 这样的单行文本。
        // trim() 的作用是去掉末尾换行，便于打印。
        System.out.println("服务器响应: " + msg.trim());

        // 收到服务端明确响应后，主动关闭客户端连接。
        ctx.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 客户端处理响应时如果发生异常，打印堆栈并关闭连接。
        cause.printStackTrace();
        ctx.close();
    }
}
