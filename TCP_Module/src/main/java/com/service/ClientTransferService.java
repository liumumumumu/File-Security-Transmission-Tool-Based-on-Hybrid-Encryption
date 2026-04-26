package com.service;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.config.NodeProperties;
import com.common.config.TransferProperties;
import com.common.protocol.file.FileAcceptPacket;
import com.crypto.CryptoSupport;
import com.session.TransferTask;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ClientTransferService
{
    private final ClientConnectionManager clientConnectionManager;
    private final CryptoSupport cryptoSupport;
    private final TransferProperties transferProperties;
    private final ClientProperties clientProperties;
    private final NodeProperties nodeProperties;
    private final PushNotificationService pushNotificationService;
    private final TransferTaskRegistry transferTaskRegistry;

    private final ExecutorService executorService= Executors.newCachedThreadPool();
    private final Map<String, CompletableFuture<String>> deviceSelectionFutures = new ConcurrentHashMap<>();
    private final Map<String, Outbound>;






    //发送端传输上下文对象，在向外发送文件的过程中，把这次需要用到的状态集中保存起来
    private static final class OutboundTransferContext
    {
        private final SecretKey secretKey;//AES密钥
        private final TransferTask transferTask;//当前传输任务本身
        private final String recipientPublicKey;//接收方的公钥
        private final CompletableFuture<FileAcceptPacket> acceptFuture=new CompletableFuture<>();//等待接收方“接受文件”的异步结果
        private final Map<Integer, CompletableFuture<Boolean>> ackFutures=new ConcurrentHashMap<>();//每个文件块的ACK等待结果Map

        private OutboundTransferContext(SecretKey secretKey, TransferTask transferTask, String recipientPublicKey)
        {
            this.secretKey=secretKey;
            this.transferTask=transferTask;
            this.recipientPublicKey=recipientPublicKey;
        }
    }

    private static final class InboundTransferContext
    {
        private final SecretKey secretKey;
        private final OutputStream outputStream;
        private final TransferTask transferTask;
        private final Map<Integer, byte[]> pendingBlocks=new ConcurrentHashMap<>();
        private final Set<Integer> receivedBlockIds=new HashSet<>();
        private long receivedBytes;
        private int receivedBlocks;
        private int nextWriteBlockId;

        private InboundTransferContext(SecretKey secretKey, OutputStream outputStream, TransferTask transferTask)
        {
            this.secretKey=secretKey;
            this.outputStream=outputStream;
            this.transferTask=transferTask;
        }

        private boolean acceptBlock(int blockId, byte[] plain)throws IOException
        {
            if(receivedBlockIds.contains(blockId))
            {
                return isCompleted();
            }

            receivedBlockIds.add(blockId);
            pendingBlocks.put(blockId, plain);
            flushContiguousBlocks();
            transferTask.updateProgress(receivedBytes, receivedBlocks);
            transferTask.updateStatus(TransferTask.TRANSFERRING, "Receiving blocks");

            if(isCompleted())
            {
                outputStream.flush();
                outputStream.close();
                transferTask.updateStatus(TransferTask.COMPILED, "File received");
                return true;
            }
            return false;
        }

        private void flushContiguousBlocks() throws IOException
        {
            while(true)
            {
                byte[] chunk=pendingBlocks.remove(nextWriteBlockId);
                if(chunk==null)
                {
                    return;
                }
                outputStream.write(chunk);
                receivedBytes+=chunk.length;
                receivedBlocks++;
                nextWriteBlockId++;
            }
        }

        private boolean isCompleted()
        {
            return receivedBlocks >= transferTask.getTotalBlocks();
        }

    }
}
