# 接口清单

服务监听地址：`http://0.0.0.0:9080`

所有接口返回 `Content-Type: application/json`。除特别说明外，成功状态码为 `200`。发生异常时返回 `500`：

```json
{
  "error": "错误信息"
}
```

说明：

- PEM 字段为完整 PEM 文本字符串。
- `plain`、`cipher`、`key`、`nonce`、`ciphertext`、`tag`、`signature` 均为 Base64 字符串，除 `sign` 接口的 `data` 字段外。
- 当前实现里部分成功/状态字段使用字符串 `"true"` / `"false"`，不是 JSON boolean。

## 密钥接口

| 名词 | 方法 | 请求路径 | 请求体 |
| --- | --- | --- | --- |
| 健康检查 | `GET` | `/health` | 无 |
| 获取公钥 | `GET` | `/key/public` | 无 |
| 获取私钥 | `GET` | `/key/private` | 无 |
| 获取密钥状态 | `GET` | `/key/status` | 无 |
| 生成密钥对 | `POST` | `/key/generate` | 无 |
| 删除密钥对 | `POST` | `/key/delete` | 无 |
| 计算公钥指纹 | `POST` | `/key/fingerprint` | `{"publicKey": "PEM 公钥"}` |
| 从文本导入私钥 | `POST` | `/key/import-text` | `{"privateKey": "PEM 私钥"}` |
| 从文件导入私钥 | `POST` | `/key/import-file` | `{"path": "私钥文件路径"}` |

### 健康检查

`GET /health`

返回：

```json
{
  "status": "ok",
  "keyDir": "./crypto_keys"
}
```

### 获取公钥

`GET /key/public`

返回：

```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"
}
```

### 获取私钥

`GET /key/private`

返回：

```json
{
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
}
```

### 获取密钥状态

`GET /key/status`

返回：

```json
{
  "hasPrivateKey": "true",
  "hasPublicKey": "true"
}
```

### 生成密钥对

`POST /key/generate`

成功返回：

```json
{
  "success": "true",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"
}
```

如果密钥已存在，返回状态码 `409`：

```json
{
  "success": "false",
  "error": "Key pair already exists"
}
```

### 删除密钥对

`POST /key/delete`

返回：

```json
{
  "success": "true",
  "deletedPrivateKey": "true",
  "deletedPublicKey": "true"
}
```

### 计算公钥指纹

`POST /key/fingerprint`

请求：

```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n"
}
```

返回：

```json
{
  "fingerprint": "SHA-256 十六进制字符串"
}
```

### 从文本导入私钥

`POST /key/import-text`

请求：

```json
{
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
}
```

返回：

```json
{
  "success": "true"
}
```

### 从文件导入私钥

`POST /key/import-file`

请求：

```json
{
  "path": "/path/to/private_key.pem"
}
```

返回：

```json
{
  "success": "true"
}
```

## 签名接口

| 名词 | 方法 | 请求路径 | 请求体 |
| --- | --- | --- | --- |
| 签名 | `POST` | `/sign` | `{"data": "待签名字符串"}` |
| 验签 | `POST` | `/verify` | `{"publicKey": "PEM 公钥", "data": "原文字符串", "signature": "Base64 签名"}` |

### 签名

`POST /sign`

请求：

```json
{
  "data": "待签名字符串"
}
```

返回：

```json
{
  "signature": "Base64 签名"
}
```

### 验签

`POST /verify`

请求：

```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n",
  "data": "原文字符串",
  "signature": "Base64 签名"
}
```

返回：

```json
{
  "valid": "true"
}
```

## 加解密接口

| 名词 | 方法 | 请求路径 | 请求体 |
| --- | --- | --- | --- |
| 生成 AES-256 密钥 | `POST` | `/aes/generate` | 无 |
| RSA 加密 | `POST` | `/rsa/encrypt` | `{"publicKey": "PEM 公钥", "plain": "Base64 明文"}` |
| RSA 解密 | `POST` | `/rsa/decrypt` | `{"cipher": "Base64 密文"}` |
| AES-GCM 加密 | `POST` | `/aes-gcm/encrypt` | `{"key": "Base64 32 字节密钥", "plain": "Base64 明文"}` |
| AES-GCM 解密 | `POST` | `/aes-gcm/decrypt` | `{"key": "Base64 32 字节密钥", "nonce": "Base64 nonce", "ciphertext": "Base64 密文", "tag": "Base64 tag"}` |

### 生成 AES-256 密钥

`POST /aes/generate`

返回：

```json
{
  "key": "Base64 32 字节 AES 密钥"
}
```

### RSA 加密

`POST /rsa/encrypt`

请求：

```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----\n",
  "plain": "Base64 明文"
}
```

返回：

```json
{
  "cipher": "Base64 RSA 密文"
}
```

### RSA 解密

`POST /rsa/decrypt`

请求：

```json
{
  "cipher": "Base64 RSA 密文"
}
```

返回：

```json
{
  "plain": "Base64 明文"
}
```

### AES-GCM 加密

`POST /aes-gcm/encrypt`

请求：

```json
{
  "key": "Base64 32 字节 AES 密钥",
  "plain": "Base64 明文"
}
```

返回：

```json
{
  "nonce": "Base64 12 字节 nonce",
  "ciphertext": "Base64 密文",
  "tag": "Base64 16 字节认证标签"
}
```

### AES-GCM 解密

`POST /aes-gcm/decrypt`

请求：

```json
{
  "key": "Base64 32 字节 AES 密钥",
  "nonce": "Base64 nonce",
  "ciphertext": "Base64 密文",
  "tag": "Base64 认证标签"
}
```

返回：

```json
{
  "plain": "Base64 明文"
}
```
