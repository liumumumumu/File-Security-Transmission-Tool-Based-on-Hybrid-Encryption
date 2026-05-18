package com.client.direct.qr;

import java.io.ByteArrayOutputStream;

/**
 * Author: LQH
 * Date: 2026-05-17
 * Purpose: 将FST1的内容放进二维码
 *
 **/

public final class Base45
{
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:".toCharArray();//Base45的字符表
    private static final int[] REVERSE = new int[128];//反查表，用字符快速找到他在Base45字符表的数值

    static  //静态初始化块，类加载的时候执行一次
    {
        for(int i=0;i<REVERSE.length;i++)
        {
            REVERSE[i]=-1;
        }
        for(int i=0;i<ALPHABET.length;i++)
        {
            REVERSE[ALPHABET[i]]=i;
        }
    }

    private Base45() {}

    public static String encode(byte[] bytes)//将二进制数据编码成Base45字符串
    {
        StringBuilder result = new StringBuilder((bytes.length * 3 + 1) / 2);
        int index = 0;
        while(index + 1 < bytes.length)
        {
            int value = ((bytes[index] & 0xff) << 8) | (bytes[index + 1] & 0xff);
            int e = value / (45 * 45);
            int remainder = value % (45 * 45);
            int d = remainder / 45;
            int c = remainder % 45;
            result.append(ALPHABET[c]).append(ALPHABET[d]).append(ALPHABET[e]);
            index += 2;
        }
        if(index < bytes.length)
        {
            int value = bytes[index] & 0xff;
            int d = value / 45;
            int c = value % 45;
            result.append(ALPHABET[c]).append(ALPHABET[d]);
        }
        return result.toString();
    }

    public static byte[] decode(String value)//把Base45字符串还原成字节数组
    {
        if(value == null)
        {
            throw new IllegalArgumentException("Base45 value is required");
        }
        if(value.length() % 3 == 1)
        {
            throw new IllegalArgumentException("Invalid Base45 length");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;
        while(index < value.length())
        {
            if(index + 2 < value.length())
            {
                int c = decodeChar(value.charAt(index));
                int d = decodeChar(value.charAt(index + 1));
                int e = decodeChar(value.charAt(index + 2));
                int decoded = c + d * 45 + e * 45 * 45;
                if(decoded > 0xffff)
                {
                    throw new IllegalArgumentException("Invalid Base45 triplet");
                }
                out.write((decoded >> 8) & 0xff);
                out.write(decoded & 0xff);
                index += 3;
            }
            else
            {
                int c = decodeChar(value.charAt(index));
                int d = decodeChar(value.charAt(index + 1));
                int decoded = c + d * 45;
                if(decoded > 0xff)
                {
                    throw new IllegalArgumentException("Invalid Base45 pair");
                }
                out.write(decoded);
                index += 2;
            }
        }
        return out.toByteArray();
    }

    private static int decodeChar(char ch)//校验并且转换成单个Base45字符
    {
        if(ch >= REVERSE.length || REVERSE[ch] < 0)
        {
            throw new IllegalArgumentException("Invalid Base45 character: "+ch);
        }
        return REVERSE[ch];
    }
}
