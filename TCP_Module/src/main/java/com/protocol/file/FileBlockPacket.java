package com.protocol.file;

import com.protocol.Packet;

public class FileBlockPacket extends Packet
{
    private String transferId;
    private int blockId;//数据块的缩影
    private byte[] nonce;//AES-GCM加密模式的核心，每次加密用的随机数（不能重复）；nonce 不是“秘密”，它是“必须公开的参数”，接收方需要用它来解密
    private byte[] ciphertext;//密文
    private byte[] tag;//AES-GCM加密模式的核心，用来验证数据有没有被篡改
}
