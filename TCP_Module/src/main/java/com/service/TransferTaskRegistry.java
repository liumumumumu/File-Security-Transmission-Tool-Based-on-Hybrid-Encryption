package com.service;


import com.common.config.AuthenticationResultProperties;
import com.crypto.CryptoSupport;
import com.server.ServerClientSession;
import com.session.TransferStatus;
import com.session.TransferTask;
import io.netty.channel.Channel;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: LQH
 * Date: 2026-04-28
 * Purpose: 传输任务注册表，将文件传输任务放在内从里统一管理，
 * 将传输记录保存在本地的JSON文件里面，
 * 当应用重启后还能回复之前的任务记录
 *
 * */

@Service
public class TransferTaskRegistry
{
    private final LocalTransferHistoryService localTransferHistoryService;
    private final Map<String, TransferTask> tasksByTaskId=new ConcurrentHashMap<>();//按照taskId建立索引
    private final Map<String, TransferTask> tasksByTransferId=new ConcurrentHashMap<>();//按照transferId建立索引


    public TransferTaskRegistry(LocalTransferHistoryService localTransferHistoryService) {
        this.localTransferHistoryService = localTransferHistoryService;
    }

    @PostConstruct
    public void loadLocalHistory()  //加载本地历史记录
    {
        for(TransferTask task: localTransferHistoryService.loadTasks())
        {
            tasksByTaskId.put(task.getTaskId(), task);
            tasksByTransferId.put(task.getTransferId(), task);
        }
    }

    //注册新任务并保存到硬盘里
    //发送和接收任务的时候都会注册任务
    public void register(TransferTask task)
    {
        tasksByTaskId.put(task.getTaskId(), task);
        tasksByTransferId.put(task.getTransferId(), task);
        persist();//放入内存索引，然后写入本地历史文件
    }

    //查询任务的入口
    public Optional<TransferTask> findByTaskId(String taskId)//Optional表示这个结果可能有，也可能没有的容器；如果找到了就返回TransferTask，没找到就返回null
    {
        return Optional.ofNullable(tasksByTaskId.get(taskId));
    }
    public Optional<TransferTask>findByTransferId(String transferId)
    {
        return Optional.ofNullable(tasksByTransferId.get(transferId));
    }

    //获取任务列表
    public List<TransferTask> allTasks()
    {
        List<TransferTask> tasks=new ArrayList<>(tasksByTaskId.values());//取出该Map里面的所有的元素并放入一个List里面
        tasks.sort(Comparator.comparing(TransferTask::getCreatedAt).reversed());//按照创建日期倒序排序
        return tasks;
    }

    //发起持久化的入口
    public void persist()
    {
        localTransferHistoryService.saveTasks(allTasks());
    }
}
