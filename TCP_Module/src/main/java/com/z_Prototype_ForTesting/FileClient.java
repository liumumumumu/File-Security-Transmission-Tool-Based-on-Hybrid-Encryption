package com.z_Prototype_ForTesting;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import lombok.extern.slf4j.Slf4j;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * 最小化的 Netty 文件发送客户端。
 *
 * 作用：
 * 1. 连接到服务端
 * 2. 按约定协议把一个文件拆成若干消息帧发送出去
 * 3. 等待服务端返回 SUCCESS
 *
 * 当前协议和服务端保持完全一致：
 * 1. START 帧：发送文件元信息（文件名、文件大小）
 * 2. CHUNK 帧：循环发送文件内容块
 * 3. END 帧：告诉服务端文件已全部发送完毕
 */
@Slf4j
public class FileClient {

    // 协议消息类型，必须和服务端常量一致。
    private static final byte TYPE_START = 1;
    private static final byte TYPE_CHUNK = 2;
    private static final byte TYPE_END = 3;

    public static void main(String[] args) throws Exception {
        // 服务端 IP。
        String host = "127.0.0.1";

        // 服务端端口。
        int port = 9000;

        // 让客户端在启动时自行选择文件。
        Path filePath = resolveFilePath(args);
        if (filePath == null) {
            log.warn("未选择任何文件，客户端退出");
            return;
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.error("文件不存在或不是普通文件: {}", filePath.toAbsolutePath());
            return;
        }

        // 客户端只需要一个 worker 线程组处理连接和 IO。
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    // 使用 NIO 模式的 SocketChannel。
                    .channel(NioSocketChannel.class)
                    // 尽量减少小包发送延迟。
                    // 对这个示例来说影响不大，但通常客户端会这样配。
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 发送给服务端的每一帧消息前面都自动加上 4 字节长度字段。
                            // 这样服务端的 LengthFieldBasedFrameDecoder 才能正确切帧。
                            ch.pipeline().addLast(new LengthFieldPrepender(4));

                            // 客户端接收服务端响应时，服务端发的是一行文本，如 "SUCCESS\n"。
                            // 所以这里用按行解码，把换行作为一条消息的结束标志。
                            ch.pipeline().addLast(new LineBasedFrameDecoder(1024));

                            // 把服务端返回的字节流按 UTF-8 解码成字符串。
                            ch.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));

                            // 处理服务端的文本响应。
                            ch.pipeline().addLast(new ClientResponseHandler());
                        }
                    });

            // 发起连接并同步等待连接建立成功。
            Channel channel = bootstrap.connect(host, port).sync().channel();

            // 连接成功后开始发送文件。
            sendFile(channel, filePath);

            // 等待连接关闭，避免主线程提前退出。
            channel.closeFuture().sync();
        } finally {
            // 释放客户端线程资源。
            group.shutdownGracefully();
        }
    }

    private static void sendFile(Channel channel, Path filePath) throws Exception {
        // 文件名只取路径最后一级。
        String fileName = filePath.getFileName().toString();

        // 文件总大小，后续写进 START 帧。
        long fileSize = Files.size(filePath);

        // 文件名需要先编码成 UTF-8 字节数组。
        byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);

        // START 帧格式：
        // [1字节类型][4字节文件名长度][N字节文件名][8字节文件大小]
        //
        // 这里分配的缓冲区容量 = 1 + 4 + 文件名字节数 + 8
        ByteBuf startFrame = Unpooled.buffer(1 + 4 + fileNameBytes.length + 8);//startFrame才是最终要发送出去的字节流载体
        startFrame.writeByte(TYPE_START);
        startFrame.writeInt(fileNameBytes.length);
        startFrame.writeBytes(fileNameBytes);
        startFrame.writeLong(fileSize);

        // writeAndFlush(...).sync() 表示：
        // 1. 立即把这帧写出
        // 2. 等待发送动作完成后再继续
        //
        // 这种写法简单直接，适合原型验证。
        // 缺点是吞吐量不是最高，因为每发一帧都在同步等待。
        channel.writeAndFlush(startFrame).sync();

        // 打开本地文件输入流，开始分块读取文件内容。
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            // 每次读取 8192 字节。
            // 这个大小只是一个常见默认值，不是协议要求。
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                // CHUNK 帧格式：
                // [1字节类型][N字节文件内容]
                ByteBuf chunkFrame = Unpooled.buffer(1 + len);
                chunkFrame.writeByte(TYPE_CHUNK);
                chunkFrame.writeBytes(buffer, 0, len);

                // 每读到一块就立即发送一块。
                channel.writeAndFlush(chunkFrame).sync();
            }
        }

        // 所有内容块发送完毕后，再发送 END 帧，告诉服务端“文件传完了”。
        ByteBuf endFrame = Unpooled.buffer(1);
        endFrame.writeByte(TYPE_END);
        channel.writeAndFlush(endFrame).sync();

        log.info("文件发送完成: " + filePath);
    }

    private static Path resolveFilePath(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return Path.of(args[0].trim());
        }

        if (!GraphicsEnvironment.isHeadless()) {
            return chooseFileWithDialog();
        }

        return chooseFileFromConsole();
    }

    private static Path chooseFileWithDialog() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 外观设置失败不影响文件选择功能。
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择要发送的文件");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = fileChooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION || fileChooser.getSelectedFile() == null) {
            return null;
        }

        return fileChooser.getSelectedFile().toPath();
    }

    private static Path chooseFileFromConsole() {
        System.out.print("请输入要发送的文件路径: ");
        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        String input = scanner.nextLine();
        if (input == null || input.isBlank()) {
            return null;
        }

        try {
            return Path.of(input.trim());
        } catch (InvalidPathException e) {
            log.error("输入的文件路径不合法: {}", input, e);
            return null;
        }
    }
}
