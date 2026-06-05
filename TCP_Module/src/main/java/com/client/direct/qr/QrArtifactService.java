package com.client.direct.qr;

import com.common.config.LocalStorageProperties;
import com.common.util.PathInputNormalizer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
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
        cleanupExpired();
        try
        {
            String qrText = normalizeGeneratedQrText(fst1Text);
            Files.createDirectories(outputDir);

            String safeInvite = inviteId.length() <= 8 ? inviteId : inviteId.substring(0, 8);
            String baseName = role + "-" + FILE_TIME.format(Instant.now()) + "-" + safeInvite;
            Path png = outputDir.resolve(baseName + ".png");
            Path fst1 = outputDir.resolve(baseName + ".fst1");
            Path ascii = outputDir.resolve(baseName + ".ascii.txt");

            Files.writeString(fst1, qrText);
            Files.writeString(ascii, asciiQr(qrText));
            writePng(qrText, png);

            Manifest manifest = readManifest();
            manifest.entries.add(new ManifestEntry(inviteId, role, expiresAt.toString(), List.of(
                    png.toString(),
                    fst1.toString(),
                    ascii.toString()
            )));
            writeManifest(manifest);
            return new QrArtifact(inviteId, role, expiresAt, png, fst1, ascii);
        }
        catch(IOException | WriterException ex)
        {
            throw new IllegalStateException("Unable to write QR artifacts", ex);
        }
    }

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

    public String readFst1Text(String valueOrPath)
    {
        String trimmed = valueOrPath == null ? "" : valueOrPath.trim();
        String compacted = compactWhitespace(trimmed);
        if(trimmed.startsWith(DirectQrCodec.PREFIX) || compacted.startsWith(DirectQrCodec.PREFIX))
        {
            return compacted;
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
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

            Result result = new MultiFormatReader().decode(bitmap, hints);
            return result.getText().trim();
        }
        catch(IOException | ReaderException ex)
        {
            throw new IllegalArgumentException("QR code import failed: unable to decode PNG: " + path, ex);
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
