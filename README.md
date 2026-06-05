# File Security Transmission Tool Based on Hybrid Encryption

基于混合加密的文件安全传输工具，课程大作业题目 17。

## 当前结构

```text
.
├── Encryption_module/    # Python 加密模块：AES-GCM、RSA、签名验签、加密服务
├── UI_Module/            # UI 相关资料
├── LICENSE
└── README.md
```

## Python 加密模块

Python 加密相关代码已统一整理到 `Encryption_module/`：

- `Encryption_module/crypto/`：LLY 的 AES-256-GCM 分块加解密核心代码
- `Encryption_module/crypto_service/`：LZY 的 FastAPI 加密服务
- `Encryption_module/tests/`：AES-GCM 单元测试
- `Encryption_module/benchmark.py`：AES-GCM 性能测试
- `Encryption_module/requirements.txt`：Python 依赖

运行说明见 [Encryption_module/README.md](Encryption_module/README.md)。

## 安全说明

密钥文件、证书文件、构建产物和队友本地模块副本不应提交到 GitHub。仓库已在 `.gitignore` 中忽略 `crypto_keys*/`、`*.pem`、`*.key`、`*.jar`、`LZY/`、`LQH/`、`Version0003/` 等本地文件。
