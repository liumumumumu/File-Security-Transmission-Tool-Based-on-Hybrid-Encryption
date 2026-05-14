package com.client.service;

import com.common.config.LocalStorageProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


/**
 * Author: LQH
 * Date: 2026-04-28
 * Purpose: 将本机的文件传输任务历史保存到本地JSON文件，并在程序启动时再读回来恢复任务列表
 * 传输任务记录本地持久化
 *
 **/


@Service
@Slf4j
public class LocalTransferHistoryService {
    private final ObjectMapper objectMapper;
    private final Path historyPath;

    public LocalTransferHistoryService(ObjectMapper objectMapper, LocalStorageProperties localStorageProperties) {
        this.objectMapper = objectMapper;
        this.historyPath = Path.of(localStorageProperties.getTransferHistoryPath()).toAbsolutePath();//历史文件路径
    }

    //从本地JSON恢复任务
    public synchronized List<TransferTask> loadTasks() {
        //判断历史任务是否为空
        if (Files.notExists(historyPath))
        {
            return List.of();
        }

        try (InputStream inputStream = Files.newInputStream(historyPath, StandardOpenOption.READ)) {
            //将JSON文件读成List
            List<StoredTransferTask> storedTasks = objectMapper.readValue(inputStream, new TypeReference<List<StoredTransferTask>>() {});
            List<TransferTask> tasks = new ArrayList<TransferTask>(storedTasks.size());
            for (StoredTransferTask storedTask : storedTasks) {
                tasks.add(storedTask.toTransferTask());//将每个StoredTransferTask转成业务对象
            }
            return tasks;
        } catch (IOException e) {
            log.info("Failed to load local transfer history from {}", historyPath,e);
            return List.of();
        }
    }

    //把当前任务列表写入本地JSON文件
    public synchronized void saveTasks(List<TransferTask> tasks)
    {
        try
        {
            Path parent=historyPath.getParent();
            if(parent!=null)//判断父目录
            {
                Files.createDirectories(parent);
            }
            //打开历史文件；文件不存在就创建，文件存在就清空重写，以写入模式打开
            try(OutputStream outputStream=Files.newOutputStream(historyPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                //把TransferTask转成StoredTransferTask再写入JSON
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream,tasks.stream().map(StoredTransferTask::from).toList());
            }
            catch(IOException e)
            {
                log.info("Failed to save local transfer history to {}", historyPath,e);
            }
        }
        catch(IOException e)
        {
            log.info("Failed to save local transfer history to {}", historyPath,e);
        }
    }


    public Path historyPath()
    {
        return historyPath;
    }

    /*
    TransferTask作为运行时的任务对象
    StoredTransferTask作为本地存储格式
     */
    private record StoredTransferTask(
            String taskId,
            String transferId,
            TransferDirection direction,
            String fileName,
            String localPath,
            String peerDeviceId,
            long totalBytes,
            int totalBlocks,
            Instant createdAt,
            Instant transferStartedAt,
            TransferStatus status,
            long transferredBytes,
            int transferredBlocks,
            String message
    )
    {
        //TransferTask转StoredTransferTask
        private static StoredTransferTask from(TransferTask task)
        {
            return new StoredTransferTask(
                    task.getTaskId(),
                    task.getTransferId(),
                    task.getDirection(),
                    task.getFileName(),
                    task.getLocalPath(),
                    task.getPeerDeviceId(),
                    task.getTotalBytes(),
                    task.getTotalBlocks(),
                    task.getCreatedAt(),
                    task.getTransferStartedAt(),
                    task.getStatus(),
                    task.getTransferredBytes(),
                    task.getTransferredBlocks(),
                    task.getMessage()
            );
        }

        //StoredTransferTask转TransferTask
        private TransferTask toTransferTask()
        {
            TransferTask task = new TransferTask(
                    taskId,
                    transferId,
                    direction,
                    fileName,
                    localPath,
                    peerDeviceId,
                    totalBytes,
                    totalBlocks,
                    createdAt
            );
            task.restoreState(status, transferredBytes, transferredBlocks, message, transferStartedAt);
            return task;
        }
    }
}
