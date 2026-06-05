package com.client.controller;

import com.client.service.OfflineCryptoService;
import com.common.util.PathInputNormalizer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/offline")
public class OfflineController
{
    private final OfflineCryptoService offlineCryptoService;

    public OfflineController(OfflineCryptoService offlineCryptoService)
    {
        this.offlineCryptoService = offlineCryptoService;
    }

    @PostMapping("/files/encrypt")
    public ResponseEntity<Map<String, Object>> encryptFile(@RequestBody Map<String, String> request)
    {
        if(request == null || request.get("filePath") == null || request.get("filePath").isBlank())
        {
            throw new IllegalArgumentException("filePath is required");
        }
        String receiver = resolveReceiverToken(request);
        Path outputDir = optionalPath(request.get("outputDir"));
        OfflineCryptoService.Fst2EncryptResult result = offlineCryptoService.encryptFile(
                PathInputNormalizer.toPath(request.get("filePath")),
                receiver,
                outputDir
        );
        return ResponseEntity.ok(filePayload(result.success(), result.outputPath(), result.fileName(), result.fileSize(), result.totalBlocks()));
    }

    @PostMapping("/files/decrypt")
    public ResponseEntity<Map<String, Object>> decryptFile(@RequestBody Map<String, String> request)
    {
        if(request == null || request.get("fst2Path") == null || request.get("fst2Path").isBlank())
        {
            throw new IllegalArgumentException("fst2Path is required");
        }
        Path outputDir = optionalPath(request.get("outputDir"));
        OfflineCryptoService.Fst2DecryptResult result = offlineCryptoService.decryptFile(
                PathInputNormalizer.toPath(request.get("fst2Path")),
                outputDir
        );
        return ResponseEntity.ok(filePayload(result.success(), result.outputPath(), result.fileName(), result.fileSize(), result.totalBlocks()));
    }

    @PostMapping("/text/encrypt")
    public ResponseEntity<Map<String, Object>> encryptText(@RequestBody Map<String, String> request)
    {
        if(request == null || request.get("text") == null)
        {
            throw new IllegalArgumentException("text is required");
        }
        String receiver = resolveReceiverToken(request);
        OfflineCryptoService.FstTextEncryptResult result = offlineCryptoService.encryptText(request.get("text"), receiver);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("payload", result.payload());
        payload.put("plaintextLength", result.plaintextLength());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/text/decrypt")
    public ResponseEntity<Map<String, Object>> decryptText(@RequestBody Map<String, String> request)
    {
        if(request == null || request.get("payload") == null || request.get("payload").isBlank())
        {
            throw new IllegalArgumentException("payload is required");
        }
        OfflineCryptoService.FstTextDecryptResult result = offlineCryptoService.decryptText(request.get("payload"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", result.success());
        payload.put("text", result.text());
        payload.put("plaintextLength", result.plaintextLength());
        return ResponseEntity.ok(payload);
    }

    private Map<String, Object> filePayload(boolean success, Path outputPath, String fileName, long fileSize, int totalBlocks)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        payload.put("outputPath", outputPath.toString());
        payload.put("fileName", fileName);
        payload.put("fileSize", fileSize);
        payload.put("totalBlocks", totalBlocks);
        return payload;
    }

    private String resolveReceiverToken(Map<String, String> request)
    {
        String publicKey = request.get("receiverPublicKey");
        String publicKeyPath = request.get("receiverPublicKeyPath");
        String contactIndex = request.get("contactIndex");
        int supplied = 0;
        supplied += publicKey != null && !publicKey.isBlank() ? 1 : 0;
        supplied += publicKeyPath != null && !publicKeyPath.isBlank() ? 1 : 0;
        supplied += contactIndex != null && !contactIndex.isBlank() ? 1 : 0;
        if(supplied != 1)
        {
            throw new IllegalArgumentException("Exactly one receiver public key source is required");
        }
        if(publicKey != null && !publicKey.isBlank())
        {
            return publicKey;
        }
        if(publicKeyPath != null && !publicKeyPath.isBlank())
        {
            return publicKeyPath;
        }
        return "contact-" + contactIndex.trim();
    }

    private Path optionalPath(String value)
    {
        return value == null || value.isBlank() ? null : PathInputNormalizer.toPath(value);
    }
}
