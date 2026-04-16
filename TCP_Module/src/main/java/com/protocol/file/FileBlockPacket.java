package com.protocol.file;

import com.protocol.MessageType;
import com.protocol.Packet;

import java.util.Arrays;

public class FileBlockPacket extends Packet
{
    private String transferId;
    private int blockId;//数据块的缩影
    private byte[] nonce;//AES-GCM加密模式的核心，每次加密用的随机数（不能重复）；nonce 不是“秘密”，它是“必须公开的参数”，接收方需要用它来解密 (会话密钥)
    //用 sessionKey（原始字节） + nonce 加密原始块，得到cipherText, tag
    private byte[] ciphertext;//密文
    private byte[] tag;//AES-GCM加密模式的核心，用来验证数据有没有被篡改

    public FileBlockPacket(int blockId, byte[] ciphertext, byte[] nonce, byte[] tag, String transferId) {
        this.blockId = blockId;
        this.ciphertext = ciphertext;
        this.nonce = nonce;
        this.tag = tag;
        this.transferId = transferId;
    }

    @Override
    public byte getMessageType()
    {
        return MessageType.File_Block;
    }

    @Override
    public String toString() {
        return "FileBlockPacket{" +
                "blockId=" + blockId +
                ", transferId='" + transferId + '\'' +
                ", nonce=" + Arrays.toString(nonce) +
                ", ciphertext=" + Arrays.toString(ciphertext) +
                ", tag=" + Arrays.toString(tag) +
                '}';
    }

    public int getBlockId() {
        return blockId;
    }

    public void setBlockId(int blockId) {
        this.blockId = blockId;
    }

    public byte[] getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(byte[] ciphertext) {
        this.ciphertext = ciphertext;
    }

    public byte[] getNonce() {
        return nonce;
    }

    public void setNonce(byte[] nonce) {
        this.nonce = nonce;
    }

    public byte[] getTag() {
        return tag;
    }

    public void setTag(byte[] tag) {
        this.tag = tag;
    }

    public String getTransferId() {
        return transferId;
    }

    public void setTransferId(String transferId) {
        this.transferId = transferId;
    }
}
