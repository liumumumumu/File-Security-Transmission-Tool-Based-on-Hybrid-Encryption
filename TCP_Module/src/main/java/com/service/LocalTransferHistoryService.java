package com.service;

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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class LocalTransferHistoryService {
    private final ObjectMapper objectMapper;
    private final Path historyPath;

    public LocalTransferHistoryService(ObjectMapper objectMapper, LocalStorageProperties localStorageProperties) {
        this.objectMapper = objectMapper;
        this.historyPath = Path.of(localStorageProperties.getTransferHistoryPath()).toAbsolutePath();
    }

    public synchronized List<TransferTask> loadTasks() {
        if (Files.notExists(historyPath)) {
            return List.of();
        }

        try (InputStream inputStream = Files.newInputStream(historyPath, StandardOpenOption.READ)) {
            List<StoredTransferTask> storedTasks = objectMapper.readValue(inputStream, new TypeReference<List<StoredTransferTask>>() {
            });
            List<TransferTask> tasks = new ArrayList<TransferTask>(storedTasks.size());
            for (StoredTransferTask storedTask : storedTasks) {
                tasks.add(storedTask.to);
            }
        } catch (Exception e) {

        }
    }


    public class StoredTransferTask {
        private String taskId;
        private String transferId;
        private TransferDirection direction;
        private String fileName;
        private String localPath;
        private String peerDeviceId;//发送方的公钥
        private long totalBytes;//总字节数
        private int totalBlocks;//总块数
        private Instant createdAt;

        private TransferStatus status = TransferStatus.PENDING;
        private long transferredBytes;//已经传输的字节数
        private long transferredBlocks;//已经传输的块数
        private String message = "";

        public StoredTransferTask(Instant createdAt, TransferDirection direction, String fileName, String localPath, String message, String peerDeviceId, TransferStatus status, String taskId, int totalBlocks, long totalBytes, String transferId, long transferredBlocks, long transferredBytes) {
            this.createdAt = createdAt;
            this.direction = direction;
            this.fileName = fileName;
            this.localPath = localPath;
            this.message = message;
            this.peerDeviceId = peerDeviceId;
            this.status = status;
            this.taskId = taskId;
            this.totalBlocks = totalBlocks;
            this.totalBytes = totalBytes;
            this.transferId = transferId;
            this.transferredBlocks = transferredBlocks;
            this.transferredBytes = transferredBytes;
        }
    }
}