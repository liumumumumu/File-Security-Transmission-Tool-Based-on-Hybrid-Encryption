# Encryption Module

本目录汇总本项目的 Python 加密相关代码，是仓库中保留的统一 Python 加密模块入口，便于 TCP 模块和后续 UI 模块统一对接。

## 目录结构

```text
Encryption_module/
├── crypto/
│   ├── __init__.py
│   └── aes_gcm.py              # LLY: AES-256-GCM 分块加解密核心函数
├── crypto_service/
│   ├── __init__.py
│   ├── main.py                 # LZY: FastAPI 加密服务
│   └── test_client.py          # LZY: 加密服务接口测试脚本
├── tests/
│   ├── __init__.py
│   └── test_aes_gcm.py         # LLY: AES-GCM 单元测试
├── benchmark.py                # LLY: AES-GCM 性能基准测试
├── requirements.txt            # Python 依赖
└── README.md
```

## 功能分工

- `crypto/aes_gcm.py`：提供本地函数式 AES-256-GCM 加解密能力，适合直接被文件分块传输逻辑调用。
- `crypto_service/main.py`：提供 HTTP 加密服务，包含 RSA 密钥生成、签名验签、RSA-OAEP 加解密、AES-GCM 加解密和公钥指纹计算等接口。
- `tests/test_aes_gcm.py`：验证 AES 密钥长度、nonce 随机性、加解密一致性、篡改检测和多块加解密场景。
- `benchmark.py`：测量 AES-GCM 加密/解密吞吐率，用于课程报告中的性能开销分析。

## 安装依赖

```bash
cd Encryption_module
pip install -r requirements.txt
```

## 运行 AES 单元测试

```bash
python -m unittest tests.test_aes_gcm -v
```

## 运行 AES 性能测试

```bash
python benchmark.py
```

## 启动加密服务

```bash
cd Encryption_module/crypto_service
python main.py --host 127.0.0.1 --port 20202 --key-dir crypto_keys
```

启动后可访问：

- `http://127.0.0.1:20202/health`
- `http://127.0.0.1:20202/docs`

## 密钥安全说明

服务运行时会在 `crypto_keys` 等目录下生成私钥和公钥文件。密钥文件不能上传到 GitHub，本仓库已通过 `.gitignore` 忽略：

- `crypto_keys*/`
- `*.pem`
- `*.key`
- `private_key.*`
- `public_key.*`

如需重新生成身份，请在本地删除旧密钥后重新启动服务或调用 `/key/generate`。
