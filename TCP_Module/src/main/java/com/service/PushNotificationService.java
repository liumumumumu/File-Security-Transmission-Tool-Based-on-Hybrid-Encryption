package com.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PushNotificationService
{
    private final CopyOnWriteArrayList<SseEmitter>  emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe()
    {
        SseEmitter emitter = new SseEmitter();
        emitters.add(emitter);
        emitter.onCompletion(()->emitters.remove(emitter));
        emitter.onTimeout(()->emitters.remove(emitter));
        emitter.onTimeout(()->emitters.remove(emitter));
        return emitter;
    }

    public void publish(String type, Object payload)
    {
        Map<String, Object>body=new LinkedHashMap<>();
        body.put("type",type);
        body.put("timestamp", Instant.now().toString());
        body.put("payload",payload);

        for(SseEmitter emitter : emitters)
        {
            try
            {
                emitter.send(SseEmitter.event().name(type).data(body));
            }
            catch (IOException e)
            {
                emitters.remove(emitter);
            }
        }
    }
}
