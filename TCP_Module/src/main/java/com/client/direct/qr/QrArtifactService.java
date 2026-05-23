package com.client.direct.qr;

import com.common.config.LocalStorageProperties;
import com.common.util.PathInputNormalizer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Author: LQH
 * Date: 2026-05-19
 * Purpose: 负责将DirectQrCodec生成出来的FST1文本保存成文件，并且实现从文件重新读回FST1文本
 * 1.生成PNG二维码图片
 * 2.生成.fst1原始文本文件
 * 3.生成.ascii.txt终端字符版二维码
 * 4.从PNG/ .fst1读取内容
 * 5.维护manifest.json，用于清理过期的二维码文件
 *
 * */

@Service
public class QrArtifactService
{
    //文件时间格式
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path outputDir;//二维码文件输出目录
    private final Path manifestPath;//manifest文件的路径
    private final Gson gson = new Gson();//用于读写manifest.json

    public QrArtifactService(LocalStorageProperties localStorageProperties)
    {
        this.outputDir = Path.of(localStorageProperties.getQrOutputDir()).toAbsolutePath();
        this.manifestPath = outputDir.resolve("manifest.json");
    }

    //生成二维码文件
    public synchronized QrArtifact writeArtifacts(String role, String inviteId, Instant expiresAt, String fst1Text)
    {
        cleanupExpired();//清理之前的过期的二维码
        try
        {
            Files.createDirectories(outputDir);//创建输出目录

            //生成文件名
            String safeInvite = inviteId.length() <= 8 ? inviteId : inviteId.substring(0, 8);
            String baseName = role + "-" + FILE_TIME.format(Instant.now()) + "-" + safeInvite;
            Path png = outputDir.resolve(baseName + ".png");
            Path fst1 = outputDir.resolve(baseName + ".fst1");
            Path ascii = outputDir.resolve(baseName + ".ascii.txt");

            //将内容写入二维码文件里面
            Files.writeString(fst1, fst1Text);//写入FST1文本文件
            Files.writeString(ascii, asciiQr(fst1Text));//写入ASCII字符二维码文件
            writePng(fst1Text, png);//写入PNG图片二维码

            //更新manifest文件
            Manifest manifest = readManifest();
            manifest.entries.add(new ManifestEntry(inviteId, role, expiresAt.toString(), List.of(
                    png.toString(),
                    fst1.toString(),
                    ascii.toString()
            )));
            writeManifest(manifest);//将这次生成的文件路径记录进manifest.json方便之后删除或清理
            return new QrArtifact(inviteId, role, expiresAt, png, fst1, ascii);
        }
        catch(IOException | WriterException ex)
        {
            throw new IllegalStateException("Unable to write QR artifacts", ex);
        }
    }

    //清理过期的二维码，读取manifest.json，并删除已经过期的二维码文件
    public synchronized int cleanupExpired()
    {
        Manifest manifest = readManifest();
        Instant now = Instant.now();//当前时间
        int removed = 0;
        Iterator<ManifestEntry> iterator = manifest.entries.iterator();
        while(iterator.hasNext())
        {
            ManifestEntry entry = iterator.next();
            Instant expiresAt = Instant.parse(entry.expiresAt);//如果expiresAt不晚于当前时间，就认为过期
            boolean expired = !expiresAt.isAfter(now);
            boolean missingAll = entry.files.stream().noneMatch(path -> Files.exists(Path.of(path)));//如果manifest中记录的所有文件都不存在，也移除这个manifest条目
            if(expired || missingAll)
            {
                for(String file : entry.files)
                {
                    try
                    {
                        Files.deleteIfExists(Path.of(file));
                    }
                    catch(IOException ignored)
                    {
                    }
                }
                iterator.remove();
                removed++;
            }
        }
        writeManifest(manifest);
        return removed;//返回清理掉的记录数量（不是文件数量）
    }

    //按照邀请删除文件，用于删除某个邀请ID对应的二维码文件
    //发送方完成握手后就删除自己的邀请二维码避免堆积
    public synchronized void deleteInvite(String inviteId)
    {
        Manifest manifest = readManifest();
        Iterator<ManifestEntry> iterator = manifest.entries.iterator();
        while(iterator.hasNext())
        {
            ManifestEntry entry = iterator.next();
            if(!entry.inviteId.equals(inviteId))//找到对应的inviteId，然后删除该记录下的所有文件
            {
                continue;
            }
            for(String file : entry.files)
            {
                try
                {
                    Files.deleteIfExists(Path.of(file));
                }
                catch(IOException ignored)
                {
                }
            }
            iterator.remove();//最后从 manifest 移除该记录：
        }
        writeManifest(manifest);
    }

    //读取FST1文件，支持两种输入1.二维码文本；2.输入路径
    public String readFst1Text(String valueOrPath)
    {
        String trimmed = valueOrPath == null ? "" : valueOrPath.trim();
        String compacted = compactWhitespace(trimmed);
        if(trimmed.startsWith(DirectQrCodec.PREFIX))//二维码文本输入
        {
            return compacted;
        }
        if(compacted.startsWith(DirectQrCodec.PREFIX))//支持为终端粘贴拆成多行的FST1文本
        {
            return compacted;
        }
        if(trimmed.startsWith("file "))//输入的是文件路径
        {
            trimmed = trimmed.substring("file ".length()).trim();
        }
        Path path = PathInputNormalizer.toPath(trimmed);
        String text = readQrText(path);
        if(!text.startsWith(DirectQrCodec.PREFIX))
        {
            throw new IllegalArgumentException("QR code import failed: content is not FST1 text: "+path);
        }
        return text;
    }

    private String compactWhitespace(String value)
    {
        StringBuilder compacted = new StringBuilder(value.length());
        for(int i = 0; i < value.length(); i++)
        {
            char ch = value.charAt(i);
            if(!Character.isWhitespace(ch))
            {
                compacted.append(ch);
            }
        }
        return compacted.toString();
    }

    public String readQrText(String valueOrPath)
    {
        String trimmed = valueOrPath == null ? "" : valueOrPath.trim();
        if(trimmed.isBlank())
        {
            throw new IllegalArgumentException("QR text or path is required");
        }
        if(trimmed.startsWith("file "))//输入的是文件路径
        {
            trimmed = trimmed.substring("file ".length()).trim();
        }
        return readQrText(PathInputNormalizer.toPath(trimmed));
    }

    public String readQrText(Path path)
    {
        if(isPngPath(path))//判断是否是PNG,是PNG就从图中识别二维码文本
        {
            return readPngText(path);
        }
        try //不是就按普通文本文件读取
        {
            return Files.readString(path).trim();
        }
        catch(IOException ex)
        {
            throw new IllegalArgumentException("Unable to read QR text file: "+path, ex);
        }
    }

    public Path outputDir()
    {
        return outputDir;
    }

    //判断PNG路径
    //根据后缀名来判断
    private boolean isPngPath(Path path)
    {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    //从PNG识别二维码
    private String readPngText(Path path)
    {
        try
        {
            BufferedImage image = ImageIO.read(path.toFile());//读取图片
            if(image == null)
            {
                throw new IOException("Unsupported PNG image");
            }
            //将图片转成ZXing可以识别的二值图
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));//BufferedImageLuminanceSource将图片转成灰度亮度源；HybridBinarizer将灰度图转成黑白图；BinaryBitmap ZXing解码器使用的图像格式
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);//识别二维码
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

            //解码
            Result result = new MultiFormatReader().decode(bitmap, hints);
            String text = result.getText().trim();

            //校验二维码内容是否属于本系统的格式
            return text;
        }
        catch(IOException | ReaderException ex)
        {
            throw new IllegalArgumentException("QR code import failed: unable to decode PNG: "+path, ex);
        }
    }

    //程序启动完成后就自动执行一次短期二维码清理
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup()
    {
        cleanupExpired();
    }

    //生成PNG二维码
    private void writePng(String text, Path path) throws WriterException, IOException
    {
        //用ZXing生成二维码图片
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);

        //二维码矩阵
        BitMatrix matrix = new QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                720,
                720,
                hints);
        MatrixToImageWriter.writeToPath(matrix, "PNG", path);//将二维码矩阵写入PNG文件
    }

    //生成ASCII二维码，生成在终端可以显示的字符二维码
    private String asciiQr(String text) throws WriterException
    {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        //生成二维码矩阵
        BitMatrix matrix = new QRCodeWriter().encode(
                text,
                BarcodeFormat.QR_CODE,
                0,
                0,
                hints);
        StringBuilder builder = new StringBuilder();

        //逐行扫描二维码举证
        for(int y = 0; y < matrix.getHeight(); y += 2)
        {
            for(int x = 0; x < matrix.getWidth(); x++)
            {
                boolean upper = matrix.get(x, y);
                boolean lower = y + 1 < matrix.getHeight() && matrix.get(x, y + 1);
                if(upper && lower)
                {
                    builder.append('█');
                }
                else if(upper)
                {
                    builder.append('▀');
                }
                else if(lower)
                {
                    builder.append('▄');
                }
                else
                {
                    builder.append(' ');
                }
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    //读取manifest
    private Manifest readManifest()
    {
        if(Files.notExists(manifestPath))
        {
            return new Manifest();//manifest.json文件不存在的话就创建一个
        }
        try
        {
            Manifest manifest = gson.fromJson(Files.readString(manifestPath), Manifest.class);
            return manifest == null ? new Manifest() : manifest;
        }
        catch(IOException | JsonSyntaxException ex)
        {
            return new Manifest();
        }
    }

    //写入manifest文件
    private void writeManifest(Manifest manifest)
    {
        try
        {
            Files.createDirectories(outputDir);//确保目录存在
            Files.writeString(manifestPath, gson.toJson(manifest));
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Unable to write QR manifest", ex);
        }
    }

    //标识整个manifest文件
    private static class Manifest
    {
        private List<ManifestEntry> entries = new ArrayList<>();
    }

    //二维码生成记录
    private record ManifestEntry(String inviteId, String role, String expiresAt, List<String> files) {
    }
}
