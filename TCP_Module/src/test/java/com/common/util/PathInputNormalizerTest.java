package com.common.util;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class PathInputNormalizerTest
{
    @Test
    public void normalizeRemovesFileKeywordAndQuotes()
    {
        assertEquals("/tmp/qr code.png", PathInputNormalizer.normalize("file \"/tmp/qr code.png\""));
    }

    @Test
    public void toPathSupportsFileUriWithSpaces()
    {
        Path path = PathInputNormalizer.toPath("file:///tmp/qr%20code.png");

        assertEquals(Path.of("/tmp/qr code.png"), path);
    }

    @Test
    public void normalizePreservesWindowsDrivePath()
    {
        assertEquals("C:\\Users\\KimMinGyu\\Downloads\\testqrcode.png",
                PathInputNormalizer.normalize("\"C:\\Users\\KimMinGyu\\Downloads\\testqrcode.png\""));
    }

    @Test
    public void normalizeSupportsWindowsFileUriWithBackslashes()
    {
        assertEquals("file:///C:/Users/KimMinGyu/Downloads/testqrcode.png",
                PathInputNormalizer.normalize("file:///\\C:\\Users\\KimMinGyu\\Downloads\\testqrcode.png"));
    }
}
