package com.client.direct.qr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: LQH
 * Date: 2026-05-17
 * Purpose: QR/ FST1协议的一个CBOR解码器(Concise Binary Object Representation)
 *
 **/


public final class CborLite //负责写
{
    private CborLite()
    {
    }

    //将Java Map对象编码成CBOR字节数组
    public static byte[] encodeCanonical(Map<String, Object> map)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeMap(out, map);
        return out.toByteArray();
    }

    //把CBOR字节数组解码回Java Map
    @SuppressWarnings("unchecked")
    public static Map<String, Object> decodeMap(byte[] bytes)
    {
        Object value = new Reader(bytes).read();
        if(!(value instanceof Map<?, ?> map))
        {
            throw new IllegalArgumentException("CBOR root must be a map");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for(Map.Entry<?, ?> entry : map.entrySet())
        {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    //这是编码是的核心分发函数
    @SuppressWarnings("unchecked")
    private static void writeValue(ByteArrayOutputStream out, Object value)
    {
        if(value == null)
        {
            out.write(0xf6);
            return;
        }
        if(value instanceof String string)
        {
            writeString(out, string);
            return;
        }
        if(value instanceof byte[] bytes)
        {
            writeTypeAndLength(out, 2, bytes.length);
            out.writeBytes(bytes);
            return;
        }
        if(value instanceof Integer || value instanceof Long)
        {
            writeInteger(out, ((Number)value).longValue());
            return;
        }
        if(value instanceof Boolean bool)
        {
            out.write(bool ? 0xf5 : 0xf4);
            return;
        }
        if(value instanceof List<?> list)
        {
            writeTypeAndLength(out, 4, list.size());
            for(Object item : list)
            {
                writeValue(out, item);
            }
            return;
        }
        if(value instanceof Map<?, ?> map)
        {
            writeMap(out, (Map<String, Object>) map);
            return;
        }
        throw new IllegalArgumentException("Unsupported CBOR value: "+value.getClass().getName());
    }

    //将Map编码成CBOR map
    private static void writeMap(ByteArrayOutputStream out, Map<String, Object> map)
    {
        List<Map.Entry<String, Object>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.comparing(entry -> encodedString(entry.getKey()), CborLite::compareBytes));
        writeTypeAndLength(out, 5, entries.size());
        for(Map.Entry<String, Object> entry : entries)
        {
            writeString(out, entry.getKey());
            writeValue(out, entry.getValue());
        }
    }

    //把字符串按CBOR字符串格式编码成byte[]
    private static byte[] encodedString(String value)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeString(out, value);
        return out.toByteArray();
    }

    //按无符号字节比较两个字节数组
    private static int compareBytes(byte[] left, byte[] right)
    {
        int min = Math.min(left.length, right.length);
        for(int i = 0; i < min; i++)
        {
            int diff = (left[i] & 0xff) - (right[i] & 0xff);
            if(diff != 0)
            {
                return diff;
            }
        }
        return left.length - right.length;
    }

    //写CBOR字符串
    private static void writeString(ByteArrayOutputStream out, String value)
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeTypeAndLength(out, 3, bytes.length);
        out.writeBytes(bytes);
    }

    //写整数
    private static void writeInteger(ByteArrayOutputStream out, long value)
    {
        if(value >= 0)
        {
            writeTypeAndLength(out, 0, value);
            return;
        }
        writeTypeAndLength(out, 1, -1 - value);
    }

    private static void writeTypeAndLength(ByteArrayOutputStream out, int majorType, long length)
    {
        int prefix = majorType << 5;
        if(length < 24)
        {
            out.write(prefix | (int) length);
        }
        else if(length <= 0xff)
        {
            out.write(prefix | 24);
            out.write((int) length);
        }
        else if(length <= 0xffff)
        {
            out.write(prefix | 25);
            out.write((int) (length >> 8));
            out.write((int) length);
        }
        else if(length <= 0xffffffffL)
        {
            out.write(prefix | 26);
            for(int shift = 24; shift >= 0; shift -= 8)
            {
                out.write((int) (length >> shift));
            }
        }
        else
        {
            out.write(prefix | 27);
            for(int shift = 56; shift >= 0; shift -= 8)
            {
                out.write((int) (length >> shift));
            }
        }
    }

    private static final class Reader   //负责读
    {
        private final ByteArrayInputStream in;

        private Reader(byte[] bytes)
        {
            this.in = new ByteArrayInputStream(bytes);
        }

        private Object read()
        {
            int initial = readByte();
            int major = initial >> 5;
            int additional = initial & 0x1f;
            return switch(major)
            {
                case 0 -> readLength(additional);
                case 1 -> -1 - readLength(additional);
                case 2 -> readBytes((int) readLength(additional));
                case 3 -> new String(readBytes((int) readLength(additional)), StandardCharsets.UTF_8);
                case 4 -> readArray((int) readLength(additional));
                case 5 -> readMap((int) readLength(additional));
                case 7 -> readSimple(additional);
                default -> throw new IllegalArgumentException("Unsupported CBOR major type: "+major);
            };
        }

        //读简单值
        private Object readSimple(int additional)
        {
            return switch(additional)
            {
                case 20 -> false;
                case 21 -> true;
                case 22 -> null;
                default -> throw new IllegalArgumentException("Unsupported CBOR simple value: "+additional);
            };
        }

        //读数组
        private List<Object> readArray(int size)
        {
            List<Object> values = new ArrayList<>(size);
            for(int i = 0; i < size; i++)
            {
                values.add(read());
            }
            return values;
        }

        //读CBOR map
        private Map<String, Object> readMap(int size)
        {
            Map<String, Object> values = new LinkedHashMap<>();
            for(int i = 0; i < size; i++)
            {
                Object key = read();
                if(!(key instanceof String stringKey))
                {
                    throw new IllegalArgumentException("CBOR map keys must be strings");
                }
                values.put(stringKey, read());
            }
            return values;
        }

        //读取指定长度的字节
        private byte[] readBytes(int length)
        {
            byte[] bytes = new byte[length];
            int read = in.read(bytes, 0, length);
            if(read != length)
            {
                throw new IllegalArgumentException("Unexpected CBOR EOF");
            }
            return bytes;
        }

        private long readLength(int additional)
        {
            if(additional < 24)
            {
                return additional;
            }
            if(additional == 24)
            {
                return readByte();
            }
            if(additional == 25)
            {
                return ((long) readByte() << 8) | readByte();
            }
            if(additional == 26)
            {
                long value = 0;
                for(int i = 0; i < 4; i++)
                {
                    value = (value << 8) | readByte();
                }
                return value;
            }
            if(additional == 27)
            {
                long value = 0;
                for(int i = 0; i < 8; i++)
                {
                    value = (value << 8) | readByte();
                }
                return value;
            }
            throw new IllegalArgumentException("Indefinite CBOR length is not supported");
        }

        //读字节
        private int readByte()
        {
            int value = in.read();
            if(value < 0)
            {
                throw new IllegalArgumentException("Unexpected CBOR EOF");
            }
            return value;
        }
    }
}
