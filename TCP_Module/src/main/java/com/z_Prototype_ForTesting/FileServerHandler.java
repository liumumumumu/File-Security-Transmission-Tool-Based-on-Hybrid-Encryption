package com.z_Prototype_ForTesting;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 服务端文件接收处理器。
 *
 * 它处理的是已经被 LengthFieldBasedFrameDecoder 切好的“单帧消息体”。
 * 也就是说，channelRead0 每次拿到的 msg 都是一条完整消息，不需要自己再考虑粘包/拆包。
 *
 * 自定义协议分为三种消息类型：
 * 1. TYPE_START：通知服务端，接下来要开始传一个文件，并附带文件名、文件总大小
 * 2. TYPE_CHUNK：真正的文件内容分块
 * 3. TYPE_END：通知服务端，文件已经传输结束
 *
 * 注意：
 * 这里是“一个连接上传一个文件”的最小模型。
 * 如果后续要支持一个连接上传多个文件，需要把状态管理再往前推进一层。
 */
@Slf4j
public class FileServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    // 协议中的消息类型常量。
    // 客户端和服务端必须保持一致，否则双方会按不同规则解析同一段字节流。
    private static final byte TYPE_START = 1;
    private static final byte TYPE_CHUNK = 2;
    private static final byte TYPE_END = 3;

    // 当前正在写入的文件输出流。
    // 当收到 START 帧时创建，收到 END 帧或连接异常断开时关闭。
    private OutputStream outputStream;

    // 当前文件的文件名。
    private String fileName;

    // 客户端声明的文件总大小。
    private long expectedFileSize;

    // 服务端实际已接收的字节数。
    // 用于在结束时做一个最基础的完整性校验。
    private long receivedFileSize;

    //接收并分发服务端收到的每一条完整协议
    //相当于协议入口
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        // 每一帧消息的第 1 个字节固定表示“消息类型”。
        byte type = msg.readByte();//这里就是读取控制信息（自定义数据包）

        // 根据类型分发到不同处理方法。
        switch (type) {
            case TYPE_START -> handleStart(msg);//表示开始传输文件
            case TYPE_CHUNK -> handleChunk(msg);//表示这个是文件的内容块
            case TYPE_END -> handleEnd(ctx);//表示文件上传完毕
            default -> throw new IllegalStateException("未知消息类型: " + type);
        }
    }

    private void handleStart(ByteBuf msg) throws IOException {
        // START 帧格式：
        // [1字节类型][4字节文件名长度][N字节文件名][8字节文件总大小]

        // 先读文件名长度。
        int fileNameLength = msg.readInt();

        // 再按长度读出文件名字节数组。
        byte[] fileNameBytes = new byte[fileNameLength];
        msg.readBytes(fileNameBytes);

        // 按 UTF-8 还原文件名。
        // sanitizeFileName 的作用是做一次简单净化，避免客户端传类似 ../../xx 这样的路径。
        this.fileName = sanitizeFileName(new String(fileNameBytes, StandardCharsets.UTF_8));

        // 读取客户端声明的文件总大小。
        this.expectedFileSize = msg.readLong();

        // 新文件开始接收时，累计接收字节数清零。
        this.receivedFileSize = 0;

        // 如果 uploads 目录不存在，则先创建。
        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);

        // 最终保存位置：uploads/文件名
        Path targetFile = uploadDir.resolve(fileName);

        //创建一个文件，然后将接收到的数据块填入到这个文件里面
        // 以“创建/覆盖写入”的方式打开输出流：
        // CREATE：文件不存在则创建
        // TRUNCATE_EXISTING：文件已存在则先清空
        // WRITE：可写
        this.outputStream = Files.newOutputStream(
                targetFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        log.info("开始接收文件: " + fileName + ", 大小: " + expectedFileSize + " bytes");
    }

    private void handleChunk(ByteBuf msg) throws IOException {
        // 如果还没收到 START 帧，就先收到了内容块，说明协议顺序错了。
        if (outputStream == null) {
            throw new IllegalStateException("还没有收到 START 帧");
        }

        // 当前这一帧除了前面的 type 字节外，剩余部分全部是文件内容。
        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);

        // 把这块数据直接写入目标文件。
        outputStream.write(data);

        // 更新累计接收长度。
        receivedFileSize += data.length;
    }

    private void handleEnd(ChannelHandlerContext ctx) throws IOException {
        // 如果连 START 都没收到，就收到了 END，同样属于协议顺序错误。
        if (outputStream == null) {
            throw new IllegalStateException("还没有收到 START 帧");
        }

        // flush 把缓冲区中可能尚未落盘的数据尽快写出。
        outputStream.flush();

        // 关闭文件流，释放文件句柄。
        outputStream.close();
        outputStream = null;

        // 这里做一个最简单的完整性检查：
        // 实际收到的总字节数必须和客户端在 START 帧里声明的文件大小一致。
        //
        // 这不是严格的防篡改校验，只是长度校验。
        // 真要做完整性校验，后续应该再加 MD5 或 SHA-256。
        if (receivedFileSize != expectedFileSize) {
            throw new IllegalStateException(
                    "文件大小不一致，expected=" + expectedFileSize + ", actual=" + receivedFileSize
            );
        }

        log.info("文件接收完成: " + fileName);

        // 服务端返回一个简单文本响应给客户端。
        // 这里追加 \n，是因为客户端用了 LineBasedFrameDecoder，
        // 客户端要靠换行符来识别一条完整响应消息。
        ctx.writeAndFlush("SUCCESS\n").addListener(f -> ctx.close());
    }

    //把客户端传过来的文件名“净化”成一个安全的纯文件名，避免它夹带目录路径。
    private String sanitizeFileName(String input) {
        // 只保留路径最后一级文件名，去掉目录部分。
        // 例如：
        // "a/b/test.txt" -> "test.txt"
        // "../evil.txt" -> "evil.txt"
        //
        // 这是一种最基础的路径穿越防护。
        return Paths.get(input).getFileName().toString();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 如果连接被关闭，而文件流还开着，做一次兜底关闭。
        closeStreamQuietly();
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 发生异常时：
        // 1. 尝试关闭文件流
        // 2. 打印异常
        // 3. 关闭连接
        closeStreamQuietly();
        cause.printStackTrace();
        ctx.close();
    }

    //安全的关闭文件输出流，并且在失败时不再往外抛异常
    private void closeStreamQuietly() {
        // 安静关闭：即使关闭过程本身抛异常，也不继续向外抛。
        // 这种写法常用于清理阶段，避免清理逻辑覆盖真正的业务异常。
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
            outputStream = null;
        }
    }
}
