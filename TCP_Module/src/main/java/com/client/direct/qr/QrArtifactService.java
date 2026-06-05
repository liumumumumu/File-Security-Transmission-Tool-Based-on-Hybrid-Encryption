package com.client.direct.qr;

import com.common.config.LocalStorageProperties;
import com.common.util.PathInputNormalizer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
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

@Service
public class QrArtifactService
{
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path outputDir;
    private final Path manifestPath;
    private final Gson gson = new Gson();

    public QrArtifactService(LocalStorageProperties localStorageProperties)
    {
        this.outputDir = Path.of(localStorageProperties.getQrOutputDir()).toAbsolutePath();
        this.manifestPath = outputDir.resolve("manifest.json");
    }

    public synchronized QrArtifact writeArtifacts(String role, String inviteId, Instant expiresAt, String fst1Text)
    {
        return writeArtifacts(role, inviteId, expiresAt, fst1Text, ".fst1", true);
    }

    public synchronized QrArtifact writeArtifacts(String role, String inviteId, Instant expiresAt, String text, String textExtension)
    {
        return writeArtifacts(role, inviteId, expiresAt, text, textExtension, true);
    }

    public synchronized QrArtifact writePermanentArtifacts(String role, String id, String text, String textExtension)
    {
        return writeArtifacts(role, id, null, text, textExtension, false);
    }

    private synchronized QrArtifact writeArtifacts(String role, String inviteId, Instant expiresAt, String fst1Text, String textExtension, boolean trackManifest)
    {
        if(trackManifest)
        {
            cleanupExpired();//清理之前的过期的二维码
        }
        try
        {
            String qrText = normalizeGeneratedQrText(fst1Text);
            Files.createDirectories(outputDir);//创建输出目录

            String safeInvite = inviteId.length() <= 8 ? inviteId : inviteId.substring(0, 8);
            String baseName = role + "-" + FILE_TIME.format(Instant.now()) + "-" + safeInvite;
            Path png = outputDir.resolve(baseName + ".png");
            String normalizedExtension = textExtension == null || textExtension.isBlank() ? ".fst1" : textExtension;
            if(!normalizedExtension.startsWith("."))
            {
                normalizedExtension = "." + normalizedExtension;
            }
            Path fst1 = outputDir.resolve(baseName + normalizedExtension);
            Path ascii = outputDir.resolve(baseName + ".ascii.txt");

            //将内容写入二维码文件里面
            Files.writeString(fst1, qrText);//写入FST1文本文件
            Files.writeString(ascii, asciiQr(qrText));//写入ASCII字符二维码文件
            writePng(qrText, png);//写入PNG图片二维码

            if(trackManifest)
            {
                //更新manifest文件
                Manifest manifest = readManifest();
                manifest.entries.add(new ManifestEntry(inviteId, role, expiresAt.toString(), List.of(
                        png.toString(),
                        fst1.toString(),
                        ascii.toString()
                )));
                writeManifest(manifest);//将这次生成的文件路径记录进manifest.json方便之后删除或清理
            }
            return new QrArtifact(inviteId, role, expiresAt, png, fst1, ascii);
        }
        catch(IOException | WriterException ex)
        {
            throw new IllegalStateException("Unable to write QR artifacts", ex);
        }
    }

    private String normalizeGeneratedQrText(String text)
    {
        if(text == null || !text.startsWith(DirectQrCodec.PREFIX))
        {
            return text;
        }
        return removeLineSeparators(text.trim());
    }

    //清理过期的二维码，读取manifest.json，并删除已经过期的二维码文件
    public synchronized int cleanupExpired()
    {
        Manifest manifest = readManifest();
        Instant now = Instant.now();
        int removed = 0;
        Iterator<ManifestEntry> iterator = manifest.entries.iterator();
        while(iterator.hasNext())
        {
            ManifestEntry entry = iterator.next();
            Instant expiresAt = Instant.parse(entry.expiresAt);
            boolean expired = !expiresAt.isAfter(now);
            boolean missingAll = entry.files.stream().noneMatch(path -> Files.exists(Path.of(path)));
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
        return removed;
    }

    public synchronized void deleteInvite(String inviteId)
    {
        Manifest manifest = readManifest();
        Iterator<ManifestEntry> iterator = manifest.entries.iterator();
        while(iterator.hasNext())
        {
            ManifestEntry entry = iterator.next();
            if(!entry.inviteId.equals(inviteId))
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
            iterator.remove();
        }
        writeManifest(manifest);
    }

    //读取FST1文件，仅支持FST1/PNG文件路径，避免终端粘贴长文本破坏Base45内容。
    public String readFst1Text(String valueOrPath)
    {
        String trimmed = valueOrPath == null ? "" : valueOrPath.trim();
        if(trimmed.startsWith(DirectQrCodec.PREFIX))
        {
            throw new IllegalArgumentException("Direct FST1 text input is not supported. Please provide a .fst1 or .png file path.");
        }
        if(trimmed.startsWith("file "))
        {
            trimmed = trimmed.substring("file ".length()).trim();
        }
        Path path = PathInputNormalizer.toPath(trimmed);
        String text = normalizeFst1FileText(readQrText(path));
        if(!text.startsWith(DirectQrCodec.PREFIX))
        {
            throw new IllegalArgumentException("QR code import failed: content is not FST1 text: " + path);
        }
        return text;
    }

    private String normalizeGeneratedQrText(String text)
    {
        if(text == null || !text.startsWith(DirectQrCodec.PREFIX))
        {
            return text;
        }
        return compactWhitespace(text.trim());
    }

    private String normalizeFst1FileText(String text)
    {
        String trimmed = text == null ? "" : text.trim();
        return compactWhitespace(trimmed);
    }

    private String removeLineSeparators(String text)
    {
        StringBuilder normalized = new StringBuilder(text.length());
        for(int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if(ch != '\r' && ch != '\n' && ch != '\t')
            {
                normalized.append(ch);
            }
        }
        return normalized.toString();
    }

    public String readQrText(String valueOrPath)
    {
        String trimmed = valueOrPath == null ? "" : valueOrPath.trim();
        if(trimmed.isBlank())
        {
            throw new IllegalArgumentException("QR text or path is required");
        }
        if(trimmed.startsWith("file "))
        {
            trimmed = trimmed.substring("file ".length()).trim();
        }
        return readQrText(PathInputNormalizer.toPath(trimmed));
    }

    public String readQrText(Path path)
    {
        if(isPngPath(path))
        {
            return readPngText(path);
        }
        try
        {
            return Files.readString(path).trim();
        }
        catch(IOException ex)
        {
            throw new IllegalArgumentException("Unable to read QR text file: " + path, ex);
        }
    }

    public Path outputDir()
    {
        return outputDir;
    }

    private boolean isPngPath(Path path)
    {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private String readPngText(Path path)
    {
        try
        {
            BufferedImage image = ImageIO.read(path.toFile());
            if(image == null)
            {
                throw new IOException("Unsupported PNG image");
            }
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);//识别二维码
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");

            //解码
            Result result = decodeQrImage(image, hints);
            String text = result.getText().trim();

            //校验二维码内容是否属于本系统的格式
            return text;
        }
        catch(IOException | ReaderException ex)
        {
            throw new IllegalArgumentException("QR code import failed: unable to decode PNG: "+pngDiagnostic(path), ex);
        }
    }

    private Result decodeQrImage(BufferedImage image, Map<DecodeHintType, Object> hints) throws ReaderException
    {
        ReaderException firstFailure = null;
        for(BufferedImage variant : qrDecodeImageVariants(image))
        {
            LuminanceSource source = new BufferedImageLuminanceSource(variant);
            for(LuminanceSource candidate : List.of(source, source.invert()))
            {
                for(BinaryBitmap bitmap : List.of(
                        new BinaryBitmap(new HybridBinarizer(candidate)),
                        new BinaryBitmap(new GlobalHistogramBinarizer(candidate))))
                {
                    try
                    {
                        return new MultiFormatReader().decode(bitmap, hints);
                    }
                    catch(ReaderException ex)
                    {
                        if(firstFailure == null)
                        {
                            firstFailure = ex;
                        }
                    }
                }
            }
        }
        throw firstFailure == null ? NotFoundException.getNotFoundInstance() : firstFailure;
    }

    private List<BufferedImage> qrDecodeImageVariants(BufferedImage image)
    {
        BufferedImage normalized = normalizeQrInputImage(image);
        List<BufferedImage> variants = new ArrayList<>();
        variants.add(normalized);
        variants.add(withQuietZone(normalized, 32));
        variants.add(scaleImage(withQuietZone(normalized, 48), 2));
        if(normalized.getWidth() < 500 || normalized.getHeight() < 500)
        {
            variants.add(scaleImage(withQuietZone(normalized, 64), 4));
        }
        variants.add(rotateRight(normalized));
        variants.add(rotateRight(variants.get(variants.size() - 1)));
        variants.add(rotateRight(variants.get(variants.size() - 1)));
        return variants;
    }

    private BufferedImage normalizeQrInputImage(BufferedImage image)
    {
        BufferedImage normalized = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for(int y = 0; y < image.getHeight(); y++)
        {
            for(int x = 0; x < image.getWidth(); x++)
            {
                normalized.setRGB(x, y, compositeOnWhite(image.getRGB(x, y)));
            }
        }
        return normalized;
    }

    private BufferedImage withQuietZone(BufferedImage image, int margin)
    {
        BufferedImage padded = new BufferedImage(image.getWidth() + margin * 2, image.getHeight() + margin * 2, BufferedImage.TYPE_INT_RGB);
        fillWhite(padded);
        for(int y = 0; y < image.getHeight(); y++)
        {
            for(int x = 0; x < image.getWidth(); x++)
            {
                padded.setRGB(x + margin, y + margin, image.getRGB(x, y));
            }
        }
        return padded;
    }

    private BufferedImage scaleImage(BufferedImage image, int scale)
    {
        BufferedImage scaled = new BufferedImage(image.getWidth() * scale, image.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
        for(int y = 0; y < scaled.getHeight(); y++)
        {
            for(int x = 0; x < scaled.getWidth(); x++)
            {
                scaled.setRGB(x, y, image.getRGB(x / scale, y / scale));
            }
        }
        return scaled;
    }

    private BufferedImage rotateRight(BufferedImage image)
    {
        BufferedImage rotated = new BufferedImage(image.getHeight(), image.getWidth(), BufferedImage.TYPE_INT_RGB);
        fillWhite(rotated);
        for(int y = 0; y < image.getHeight(); y++)
        {
            for(int x = 0; x < image.getWidth(); x++)
            {
                rotated.setRGB(image.getHeight() - 1 - y, x, image.getRGB(x, y));
            }
        }
        return rotated;
    }

    private int compositeOnWhite(int argb)
    {
        int alpha = (argb >>> 24) & 0xff;
        if(alpha == 255)
        {
            return argb & 0x00ffffff;
        }
        int red = (argb >>> 16) & 0xff;
        int green = (argb >>> 8) & 0xff;
        int blue = argb & 0xff;
        red = (red * alpha + 255 * (255 - alpha)) / 255;
        green = (green * alpha + 255 * (255 - alpha)) / 255;
        blue = (blue * alpha + 255 * (255 - alpha)) / 255;
        return (red << 16) | (green << 8) | blue;
    }

    private void fillWhite(BufferedImage image)
    {
        int white = Color.WHITE.getRGB();
        for(int y = 0; y < image.getHeight(); y++)
        {
            for(int x = 0; x < image.getWidth(); x++)
            {
                image.setRGB(x, y, white);
            }
        }
    }

    private String pngDiagnostic(Path path)
    {
        try
        {
            BufferedImage image = ImageIO.read(path.toFile());
            long size = Files.exists(path) ? Files.size(path) : -1;
            if(image == null)
            {
                return path + " (unsupported image, bytes=" + size + ")";
            }
            return path + " (width=" + image.getWidth() + ", height=" + image.getHeight() + ", bytes=" + size + ")";
        }
        catch(IOException ex)
        {
            return path + " (diagnostic unavailable: " + ex.getMessage() + ")";
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup()
    {
        cleanupExpired();
    }

    private void writePng(String text, Path path) throws WriterException, IOException
    {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);

        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 720, 720, hints);
        MatrixToImageWriter.writeToPath(matrix, "PNG", path);
    }

    private String asciiQr(String text) throws WriterException
    {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
        StringBuilder builder = new StringBuilder();
        for(int y = 0; y < matrix.getHeight(); y++)
        {
            for(int x = 0; x < matrix.getWidth(); x++)
            {
                builder.append(matrix.get(x, y) ? "##" : "  ");
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private Manifest readManifest()
    {
        if(Files.notExists(manifestPath))
        {
            return new Manifest();
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

    private void writeManifest(Manifest manifest)
    {
        try
        {
            Files.createDirectories(outputDir);
            Files.writeString(manifestPath, gson.toJson(manifest));
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Unable to write QR manifest", ex);
        }
    }

    private static class Manifest
    {
        private List<ManifestEntry> entries = new ArrayList<>();
    }

    private record ManifestEntry(String inviteId, String role, String expiresAt, List<String> files) {
    }
}
