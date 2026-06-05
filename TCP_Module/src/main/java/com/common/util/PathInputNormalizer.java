package com.common.util;

import java.net.URI;
import java.nio.file.Path;

/**
 * Author: LQH
 * Date: 2026-05-10
 * Purpose: 输入路径清洗工具类，将用户复制过来的路径统一转化为Java可用的Path
 *
 * */

public final class PathInputNormalizer
{
    private PathInputNormalizer() {}

    public static Path toPath(String input)
    {
        String normalized = normalize(input);//通过normalize来规格化字符串
        if (normalized.startsWith("file:/")) {
            return Path.of(URI.create(escapeFileUri(normalized)));
        }
        return Path.of(normalized);//再把规格化后的字符串转成Path
    }

    public static String normalize(String input)
    {
        if (input == null) {    //验证有效输入
            throw new IllegalArgumentException("path is required");
        }

        String normalized = input.trim();//去掉前后空白
        if (normalized.regionMatches(true, 0, "file ", 0, "file ".length())) {
            normalized = normalized.substring("file ".length()).trim();
        }
        while (isWrapped(normalized, '"') || isWrapped(normalized, '\'')) {     //去掉包裹路径的引号
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.startsWith("file:///") && normalized.length() > "file:///".length() && normalized.charAt("file:///".length()) == '\\') {
            normalized = "file:///" + normalized.substring("file:///".length()).replace('\\', '/');
        }
        return normalized;
    }

    private static String escapeFileUri(String value)//给file:/...形式的路径做一个很小的URI容错处理
    {
        return value.replace(" ", "%20");
    }

    //判断字符串是不是被引号完整的包住
    private static boolean isWrapped(String value, char quote)
    {
        return value.length() >= 2 && value.charAt(0) == quote && value.charAt(value.length() - 1) == quote;
    }
}
