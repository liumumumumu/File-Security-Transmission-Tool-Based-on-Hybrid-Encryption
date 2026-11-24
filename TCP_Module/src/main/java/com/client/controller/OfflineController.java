package com.client.controller;

import com.client.service.OfflineCryptoService;
import com.common.util.PathInputNormalizer;
import com.crypto.CryptoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Author: LQH
 * Date: 2026-06-26
 * Update: 2026-07-03 — 新增异常处理器和密钥前置校验
 * Purpose: 离线加解密 REST API 控制器，
 *          提供文件/文本的 FST2/FST-TEXT1 格式加解密接口。
 *          解密操作前置检查本地密钥对是否存在，避免深层 500 错误。
 */
@RestController
@RequestMapping("/api/offline")
public class OfflineController
{
    private final OfflineCryptoService offlineCryptoService;
    private final CryptoSupport cryptoSupport;

    public OfflineController(OfflineCryptoService offlineCryptoService,
                             CryptoSupport cryptoSupport)
    {
        this.offlineCryptoService = offlineCryptoService;
        this.cryptoSupport = cryptoSupport;
    }

    /**
     * 解密操作的前置校验：确保本地密钥对已生成。
     * 如果密钥不存在则抛出 IllegalArgumentException，
     * 由 {@link #handleIllegalArgument} 捕获并返回 400 + 友好提示。
     */
    private void requirePrivateKey()
    {
        Map<String, Object> status = cryptoSupport.keyStatus();
        if(!"true".equals(status.get("hasPrivateKey")))
        {
            throw new IllegalArgumentException(
                    "No local key pair found. Please generate a key pair first via 'generate-key' or POST /api/system/key/generate.");
        }
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
        requirePrivateKey();
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
        requirePrivateKey();
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

    /**
     * 密码学操作失败 (如私钥无法解密、加密/解密运算失败)。
     * 返回 500 + 具体错误信息，方便定位问题。
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex)
    {
        log.warn("Offline crypto operation failed: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 参数校验失败 (如缺少必填字段、密钥对不存在、payload 格式错误)。
     * 返回 400 + 具体错误信息。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex)
    {
        log.warn("Offline crypto invalid argument: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 兜底异常处理器：捕获所有未被其他 Handler 覆盖的异常。
     * 返回 500 + 具体错误信息，并记录完整堆栈到日志。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex)
    {
        log.error("Unexpected error in offline controller: {}", ex.getMessage(), ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static final Logger log = LoggerFactory.getLogger(OfflineController.class);
}
