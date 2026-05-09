package com.z_Prototype_ForTesting.L0420;

import io.netty.buffer.ByteBuf;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

//nio框架。
@Slf4j
public class TestByteBuffer
{
    public static void main(String[] args)
    {
        //FileChannel
        //1. 输入输出流，2. RandomAccessFile
        try (FileChannel channel = new FileInputStream("pom.xml").getChannel())
        {
            //准备缓冲区
            ByteBuffer buffer=ByteBuffer.allocate(1024);
            //从channel 读取数据，向 Buffer 写入
            while(true)
            {
                int len=channel.read(buffer);//read函数返回的是实际的读到的字节数；返回值是-1标识文件已经读取完毕
                log.info("len: "+len);
                if(len == -1)
                {
                    break;
                }
                //输出Buffer的内容
                buffer.flip();//切换至读模式（读取这个buffer里面的内容，指针移动到buffer的首部）
                while(buffer.hasRemaining())//检查是否还有剩余的未读的数据
                {
                    byte b = buffer.get();//无参数，一个字节的读取
                    log.info(new String(new byte[]{b}));
                }
                buffer.clear();//切换为写模式（往这个buffer里面写内容，指针重新移动到buffer的首部，后续写入的时候覆盖之前的内容）
            }
        }
        catch (IOException e)
        {
            log.info(e.getMessage());
        }
    }
}
