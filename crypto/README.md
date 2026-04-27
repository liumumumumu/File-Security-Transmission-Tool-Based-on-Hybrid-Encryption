# 加密模块 (crypto)

本模块为「基于混合加密的文件安全传输工具」加密模块的一部分，由 **LLY** 负责实现。

## 职责范围

- ✅ AES-256-GCM **对称加解密**（本人负责）
- ⏳ RSA 密钥对生成、签名、AES 密钥包裹（队友 A 负责，待加入）

**不负责**：文件分块、应用层协议封装、粘包处理（TCP 模块负责）。

## 文件结构

```
crypto/
├── __init__.py
├── aes_gcm.py        # AES-256-GCM 加解密核心
└── README.md         # 本文档
```

## 接口规约（对接 TCP 模块）

### 数据结构

```python
@dataclass
class AesGcmBlock:
    nonce: bytes       # 12 字节 GCM nonce（每块随机生成）
    ciphertext: bytes  # 与明文等长的密文
    tag: bytes         # 16 字节 GCM 认证标签
```

### 函数

| 函数 | 说明 |
|------|------|
| `generate_aes_key() -> bytes` | 生成 32 字节（AES-256）随机会话密钥 |
| `encrypt_block(data: bytes, key: bytes) -> AesGcmBlock` | 加密一块明文 |
| `decrypt_block(ciphertext, nonce, tag, key) -> bytes` | 解密并验证一块密文，篡改时抛 `InvalidTag` |

### 常量

```
IV_SIZE  = 12   # nonce 长度
TAG_SIZE = 16   # GCM tag 长度
KEY_SIZE = 32   # AES-256 密钥长度
```

## 使用示例

```python
from crypto.aes_gcm import generate_aes_key, encrypt_block, decrypt_block

key = generate_aes_key()                       # 由密钥协商模块产出
block = encrypt_block(b"hello world", key)     # TCP 发送方
plaintext = decrypt_block(                     # TCP 接收方
    block.ciphertext, block.nonce, block.tag, key
)
```

## TCP 模块如何序列化一个块（建议）

每块独立加密，应用层封装格式（题目要求自定义协议）：

```
| 块序号(4B) | 块长度(4B) | nonce(12B) | ciphertext(变长) | tag(16B) |
```

发送方伪码：

```python
block = encrypt_block(file_chunk, key)
packet = struct.pack(">II", seq, len(block.ciphertext)) \
       + block.nonce + block.ciphertext + block.tag
sock.sendall(packet)
```

接收方按上述字段解析后调用 `decrypt_block(...)` 即可。

## 安全保证

- **机密性**：AES-256（256 位密钥强度）
- **完整性 + 认证**：GCM 模式，篡改任何字节（密文 / nonce / tag）解密时都会抛出 `InvalidTag`
- **nonce 唯一性**：每次加密随机 12 字节 nonce，同一密钥下生日碰撞概率约 2^-48 / 块数（>1GB 文件场景仍安全）
- **每次连接独立密钥**：会话密钥由密钥协商模块每次重新生成

## 测试

### 单元测试

```bash
python -m unittest tests.test_aes_gcm -v
```

覆盖：
- 密钥生成长度与随机性
- 空 / 短 / 1MB 大块的加解密一致性
- 同一明文每次加密 nonce / 密文均不同
- 篡改 ciphertext / nonce / tag / 密钥 / 截断密文 时抛 `InvalidTag`
- 多块循环模拟（验证 nonce 全局唯一）

### 模块自测

```bash
python crypto/aes_gcm.py
```

### 性能基准

```bash
python benchmark.py
```

测量内存拷贝（基线）vs AES-256-GCM 加密 vs 解密吞吐率（MB/s），对应题目「测量加密开销」要求。

## 依赖

```
cryptography>=41.0.0
```

安装：`pip install cryptography`
