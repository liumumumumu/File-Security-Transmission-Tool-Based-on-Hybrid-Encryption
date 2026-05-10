# 密钥协商模块 (key_exchange)

本模块为「基于混合加密的文件安全传输工具」加密模块的一部分，实现 RSA-2048 非对称加密与密钥协商。

## 职责范围

- ✅ RSA-2048 密钥对生成与序列化
- ✅ 数字签名 / 验签（身份认证）
- ✅ 用 RSA 加密/解密 AES 会话密钥（密钥协商）

## 文件结构

```
key_exchange/
├── __init__.py
├── rsa_utils.py     # RSA 密钥协商核心
└── README.md        # 本文档
```

## 接口规约（对接 TCP 模块）

| 函数 | 说明 |
|------|------|
| `generate_private_key() -> RSAPrivateKey` | 生成 RSA-2048 私钥 |
| `generate_public_key(priv) -> RSAPublicKey` | 从私钥提取公钥 |
| `export_private_key(priv) -> bytes` | 私钥导出为 PEM 格式 |
| `export_public_key(pub) -> bytes` | 公钥导出为 PEM 格式（用于网络传输） |
| `load_private_key(pem) -> RSAPrivateKey` | 从 PEM 加载私钥 |
| `load_public_key(pem) -> RSAPublicKey` | 从 PEM 加载公钥 |
| `sign(challenge, priv) -> bytes` | 用私钥签名（身份认证） |
| `verify_signature(pub, challenge, sig) -> bool` | 用公钥验签 |
| `encrypt_aes_key(aes_key, pub) -> bytes` | 用 RSA 公钥加密 AES 密钥 |
| `decrypt_aes_key(enc_key, priv) -> bytes` | 用 RSA 私钥解密 AES 密钥 |

## 使用示例

### 密钥协商

```python
from key_exchange.rsa_utils import *
from crypto.aes_gcm import generate_aes_key, encrypt_block, decrypt_block

# ── 服务端 ──
server_priv = generate_private_key()
server_pub = generate_public_key(server_priv)
pub_pem = export_public_key(server_pub)      # 发给客户端

# ── 客户端 ──
server_pub = load_public_key(pub_pem)        # 从网络接收
aes_key = generate_aes_key()                 # 生成 AES 会话密钥
encrypted_key = encrypt_aes_key(aes_key, server_pub)  # 加密后发给服务端

# ── 服务端 ──
aes_key = decrypt_aes_key(encrypted_key, server_priv)  # 解密得到 AES 密钥

# ── 双方用 AES 密钥加密通信 ──
block = encrypt_block(data, aes_key)
plaintext = decrypt_block(block.ciphertext, block.nonce, block.tag, aes_key)
```

### 身份认证

```python
# 服务端证明身份
challenge = os.urandom(32)            # 客户端生成随机 challenge
sig = sign(challenge, server_priv)    # 服务端签名
ok = verify_signature(server_pub, challenge, sig)  # 客户端验证
```

## 测试

```bash
# 单元测试
python -m unittest tests.test_rsa_utils -v

# 模块自测
python key_exchange/rsa_utils.py
```

## 依赖

```
cryptography>=41.0.0
```

安装：`pip install cryptography`
