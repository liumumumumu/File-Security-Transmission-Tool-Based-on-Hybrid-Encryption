package com.z_Prototype_ForTesting.L0422;

import io.netty.channel.DefaultEventLoop;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.NettyRuntime;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TestEventLoop
{
    public static void main(String[] args)
    {
        //1.创建事件循环组
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup(2);//处理io, 普通任务，定时任务
        //EventLoopGroup defaultEventLoopGroup = new DefaultEventLoopGroup();//处理普通任务，定时任务

        //System.out.println("核心数: "+NettyRuntime.availableProcessors());

        //-------函数--------//
        //2.获取下一个事件循环对象
        System.out.println(eventLoopGroup.next());
        System.out.println(eventLoopGroup.next());
        System.out.println(eventLoopGroup.next());//轮询

        //3.执行普通任务
        //让事件组中的某一个对象去执行
        //参数是任务对象
        eventLoopGroup.next().submit(()->{
            try
            {
                Thread.sleep(1000);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            log.info("subThread: ok");
        });
        log.info("mainThread: ok");


        //4.执行定时任务
        //参数是任务对象，初始延迟时间, 间隔时间，时间单位
        eventLoopGroup.scheduleAtFixedRate(()->{
            log.info("subThread: 定时任务, ok");
        }, 1, 10, TimeUnit.SECONDS);






    }
}
