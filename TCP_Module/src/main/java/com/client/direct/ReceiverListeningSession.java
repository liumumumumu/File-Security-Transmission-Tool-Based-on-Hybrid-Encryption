package com.client.direct;

import com.client.direct.qr.QrArtifact;
import com.client.direct.qr.ReceiverResponseQr;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 返回结果封装类
 * 保存本机IPv6监听端口，接收方响应内容，响应内容的二维码文件
 *
 * */


public class ReceiverListeningSession
{
    private ReceiverResponseQr response;//接收方响应二维码的信息
    private QrArtifact artifact;//二维码文件的输出信息
    private int port;//接收方实际监听的端口

    public ReceiverListeningSession() {}

    public ReceiverListeningSession(ReceiverResponseQr response,
                                    QrArtifact artifact,
                                    int port) {
        this.artifact = artifact;
        this.port = port;
        this.response = response;
    }

    public QrArtifact getArtifact() {
        return artifact;
    }

    public void setArtifact(QrArtifact artifact) {
        this.artifact = artifact;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public ReceiverResponseQr getResponse() {
        return response;
    }

    public void setResponse(ReceiverResponseQr response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return "ReceiverListeningSession{" +
                "artifact=" + artifact +
                ", response=" + response +
                ", port=" + port +
                '}';
    }
}
