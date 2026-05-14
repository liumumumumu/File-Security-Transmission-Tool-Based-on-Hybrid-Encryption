package com.session;

import java.time.Duration;
import java.time.Instant;

/**
 * Author: LQH
 * Date: 2026-04-26
 * Purpose: 文件传输任务状态对象
 * 负责记录一次文件传输（发送/ 接收）的基本信息和当前进度
 *
 * */

public class TransferTask
{
    private final String taskId;
    private final String transferId;
    private final TransferDirection direction;
    private final String fileName;
    private final String localPath;
    private volatile String peerDeviceId;//发送方的公钥
    private final long totalBytes;//总字节数
    private final int totalBlocks;//总块数
    private final Instant createdAt;

    private volatile TransferStatus status = TransferStatus.PENDING;
    private volatile long transferredBytes;//已经传输的字节数
    private volatile int transferredBlocks;//已经传输的块数
    private volatile String message="";
    private volatile Instant transferStartedAt;//首次进入传输状态的时间

    public TransferTask(
            String taskId,
            String transferId,
            TransferDirection direction,
            String fileName,
            String localPath,
            String peerDeviceId,
            long totalBytes,
            int totalBlocks,
            Instant createdAt
    )
    {
        this.taskId = taskId;
        this.transferId = transferId;
        this.direction = direction;
        this.fileName = fileName;
        this.localPath = localPath;
        this.peerDeviceId = peerDeviceId;
        this.totalBytes = totalBytes;
        this.totalBlocks = totalBlocks;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    //更新进度
    public synchronized void updateProgress(long transferredBytes, int transferredBlocks)
    {
        this.transferredBytes = transferredBytes;
        this.transferredBlocks = transferredBlocks;
    }

    //更新状态
    public synchronized void updateStatus(TransferStatus status, String message)
    {
        this.status = status;
        this.message = message == null ? "" : message;
        if(status == TransferStatus.TRANSFERRING && this.transferStartedAt == null)
        {
            this.transferStartedAt = Instant.now();
        }
    }

    //更新传输任务里对端的设备Id
    public synchronized void updatePeerDeviceId(String peerDeviceId)
    {
        this.peerDeviceId = peerDeviceId;
    }

    //恢复传输任务状态，用于程序启动后，从本地历史记录，数据库读取之前保存的传输任务，然后把之前的任务状态恢复回来
    public synchronized void restoreState(TransferStatus status, long transferredBytes, int transferredBlocks, String message)
    {
        restoreState(status, transferredBytes, transferredBlocks, message, null);
    }

    public synchronized void restoreState(TransferStatus status, long transferredBytes, int transferredBlocks, String message, Instant transferStartedAt)
    {
        this.status = status==null?TransferStatus.PENDING:status;
        this.transferredBytes = transferredBytes;
        this.transferredBlocks = transferredBlocks;
        this.message = message==null?"":message;
        this.transferStartedAt = transferStartedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTransferStartedAt() {
        return transferStartedAt;
    }

    public TransferDirection getDirection() {
        return direction;
    }

    public String getFileName() {
        return fileName;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPeerDeviceId() {
        return peerDeviceId;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public void setStatus(TransferStatus status) {
        this.status = status;
    }

    public String getTaskId() {
        return taskId;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getTransferId() {
        return transferId;
    }

    public int getTransferredBlocks() {
        return transferredBlocks;
    }

    public void setTransferredBlocks(int transferredBlocks) {
        this.transferredBlocks = transferredBlocks;
    }

    public long getTransferredBytes() {
        return transferredBytes;
    }

    public void setTransferredBytes(long transferredBytes) {
        this.transferredBytes = transferredBytes;
    }

    //获取从传输开始到当前时刻的平均传输速度，单位为 MB/s
    public double getAverageSpeedMegabytesPerSecond()
    {
        return getAverageSpeedMegabytesPerSecond(Instant.now());
    }

    //显示的传输速度是平均传输速度（已传输的Bytes的数/ 从传输任务开始至今的时间）
    public double getAverageSpeedMegabytesPerSecond(Instant now)
    {
        if(transferStartedAt == null || now == null || transferredBytes <= 0)
        {
            return 0D;
        }
        double elapsedSeconds = Duration.between(transferStartedAt, now).toNanos() / 1_000_000_000D;
        if(elapsedSeconds <= 0D)
        {
            return 0D;
        }
        return Math.max(0D, transferredBytes / elapsedSeconds / 1024D / 1024D);
    }

    @Override
    public String toString() {
        return "TransferTask{" +
                "createdAt=" + createdAt +
                ", taskId='" + taskId + '\'' +
                ", transferId='" + transferId + '\'' +
                ", direction=" + direction +
                ", fileName='" + fileName + '\'' +
                ", localPath='" + localPath + '\'' +
                ", peerDeviceId='" + peerDeviceId + '\'' +
                ", totalBytes=" + totalBytes +
                ", totalBlocks=" + totalBlocks +
                ", status=" + status +
                ", transferredBytes=" + transferredBytes +
                ", transferredBlocks=" + transferredBlocks +
                ", transferStartedAt=" + transferStartedAt +
                ", message='" + message + '\'' +
                '}';
    }

    //获取传输进度
    public double getProgress()
    {
        return totalBytes<=0? (double)0: (double)transferredBlocks/(double)totalBlocks;
    }
}
