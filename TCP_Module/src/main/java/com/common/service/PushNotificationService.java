package com.common.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;


/**
 * Author: LQH
 * Date: 2026-04-23
 * Purpose: 服务端把所有已经验证的客户端保存起来，当有新的世纪统一把消息推送给客户端
 * 消息提醒，状态更新，进度通知
 * note: 目前实现的是全量广播(2026-04-24)，应该改成定向推送;
 *  目前只在ClientConnectionManager里面进行了调用
 *
 * */


@Service
public class PushNotificationService
{
    //客户端连接池
    //有客户端订阅时，把他的SseEmitter放进去
    private final CopyOnWriteArrayList<SseEmitter>  emitters = new CopyOnWriteArrayList<>();    //Sse: Server-Sent Events服务器向浏览器推送的对象

    //本地通知监听器列表
    private final CopyOnWriteArrayList<BiConsumer<String, Object>>localListeners = new CopyOnWriteArrayList<>();

    //给新的客户端创建Sse连接，并将这个连接注册到当前服务里面
    public SseEmitter subscribe()
    {
        SseEmitter emitter = new SseEmitter(0L);//新来的一个客户端；0 表示不由服务端主动超时
        emitters.add(emitter);
        emitter.onCompletion(()->emitters.remove(emitter));//当连接注册结束后，就把它从列表中删掉
        emitter.onTimeout(()->emitters.remove(emitter));//当这个连接超时后，就把它从列表中删掉
        emitter.onError(ex->emitters.remove(emitter));//当连接出错后，就把它从列表中删掉
        return emitter;
    }

    //注册本地监听器，并返回一个取消订阅的方法
    public Runnable subscribeLocal(BiConsumer<String, Object> listener)
    {
        localListeners.add(listener);
        return () -> localListeners.remove(listener);
    }

    public void publish(String type, Object payload)
    {
        //推送消息的内容
        Map<String, Object>body=new LinkedHashMap<>();//LinkedHashMap保障字段按顺序插入
        body.put("type",type);
        body.put("timestamp", Instant.now().toString());
        body.put("payload",payload);

        //将通知分发给所有的本地监听器
        for(BiConsumer<String, Object> listener: localListeners)
        {
            listener.accept(type,body);
        }

        //开始广播，遍历所有emitter，一个个发送消息
        for(SseEmitter emitter : emitters)
        {
            try
            {
                //发送消息
                emitter.send(SseEmitter.event().name(type).data(body));
            }
            catch (Exception e)
            {
                emitters.remove(emitter);
                try
                {
                    emitter.complete();
                }
                catch (Exception ignored)
                {
                    // The SSE client is already gone.
                }
            }
        }
    }
}
