# 🔐 模块 A：安全密钥协商与非对称加密

## 📖 模块概述

本模块负责整个安全传输工具的“握手（Handshake）”阶段。
参考现代 TLS 1.3 协议的设计思想，本模块利用 **RSA-2048** 非对称加密算法安全地交换预主密钥（Pre-Master Secret），并引入 **HKDF-SHA256** 算法衍生出最终的 **AES-256** 会话密钥，从而为后续的文件传输提供极高强度的安全保障。

## 📁 文件结构

* `rsa_manager.py`：负责 RSA 密钥对的生成、加密落盘保存与加载。
* `key_negotiator.py`：核心协商模块，提供供客户端和服务器调用的握手接口。
* `test_handshake.py`：本地 Mock 测试脚本，用于在脱离网络环境的情况下验证握手逻辑。
* `*.pem`：生成的密钥文件（`server_private.pem` 带有本地 AES 密码保护）。

---

## 🛠️ 团队对接指南 (API Interfaces)

本模块已将底层的密码学复杂性完全封装，其他组员只需调用对应的接口即可。

### 1. 对接 TCP 通信组 (人员 C/D)

在你们建立好底层的 TCP Socket 连接后（`connect` 或 `accept` 成功后），**请在发送任何业务数据之前，先调用本模块的握手函数**。

* **客户端调用：**
```python
from rsa_manager import RSAManager
from key_negotiator import Negotiator

# 1. 加载服务器公钥 (提前放在客户端目录下)
pub_key = RSAManager.load_public_key("server_public.pem")

# 2. 执行握手 (传入你们的 socket 对象)
# 注意：该函数内部会自动执行 socket.sendall() 发送 256 字节的密文
aes_session_key = Negotiator.client_negotiate_key(client_socket, pub_key)

```


* **服务端调用：**
```python
from rsa_manager import RSAManager
from key_negotiator import Negotiator

# 1. 加载服务器私钥 (需要输入本地保护密码，代码内已配置)
priv_key = RSAManager.load_private_key("server_private.pem")

# 2. 执行握手 (传入你们 accept 得到的客户端 socket)
# 注意：该函数内部会执行 socket.recv(256) 阻塞等待客户端发来的密文
aes_session_key = Negotiator.server_negotiate_key(conn_socket, priv_key)

```



### 2. 对接对称加密组 (人员 B)

握手成功后，上述 `client_negotiate_key` 和 `server_negotiate_key` 都会返回一个变量 `aes_session_key`。

* **格式**：标准的 `bytes` 类型。
* **长度**：精确的 32 字节（256-bit）。
* **用途**：你们可以直接将这个变量作为密钥，传入你们的 `AES-GCM` 模块进行后续文件块的加密与解密。

---

## 🚀 本地测试与运行

在与其他模块联调之前，可以通过运行测试脚本验证本模块的连通性：

```bash
python test_handshake.py

```

---

## 🌟 进阶特性 (期末报告亮点素材)

本模块在实现基础功能外，包含以下工业级安全实践：

1. **私钥加密落盘**：服务器私钥（`server_private.pem`）并未以明文形式存在硬盘上，而是使用了 `BestAvailableEncryption` 结合强密码进行保护，防止服务器硬盘失窃导致私钥泄露。
2. **HKDF 密钥衍生**：摒弃了将伪随机数直接作为 AES 密钥的初级做法。客户端生成的随机数仅作为“预主密钥”，双方通过基于 HMAC 的密钥提取与扩展算法（HKDF）共同推导出最终的 AES 密钥，抗重放攻击能力更强。
3. **OAEP 填充模式**：RSA 加解密强制采用 `OAEP + SHA256` 填充策略，彻底免疫针对老旧 `PKCS1v15` 模式的 Padding Oracle 攻击。