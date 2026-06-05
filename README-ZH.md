# 基于混合加密的文件安全传输工具

一个可运行的安全文件传输工具原型，基于混合加密实现文件传输、文本消息、联系人管理、本地加密服务、Web UI 和桌面端封装。项目采用模块化结构：Java 负责客户端/服务端传输链路和本地 HTTP API，Python 提供本地加密服务，Vue 提供前端界面，Electron 提供桌面应用入口。

## 已实现功能

- 混合加密文件传输：AES-GCM 加密文件内容，RSA-OAEP 处理会话密钥交换，签名和公钥指纹用于身份相关流程。
- 在线 TCP 中继传输：客户端连接服务器，服务端负责认证、在线设备路由和传输请求转发。
- 发送/接收任务管理：支持进度查看、取消、接收确认和拒绝。
- 断点补传/重传：接收方可请求补传，发送方可处理补传请求。
- 联系人与黑名单：支持本地联系人管理、在线用户搜索和黑名单控制。
- 本地 HTTP API：Java 客户端默认暴露 `127.0.0.1:20201`，供 Web UI 或其他本地程序调用。
- Web / Desktop UI：Vue 前端可独立开发，也可由 Java 客户端托管；Electron 可将客户端封装为桌面应用。
- 离线加解密能力：提供不依赖在线传输的本地文件和文本加解密能力。

## 技术栈

### TCP / 后端

- Java 21
- Spring Boot 3.3.11
- Netty 4.1.119
- Maven
- MyBatis
- Redis / MySQL / SQLite

### 加密模块

- Python 3.10+
- FastAPI
- cryptography
- AES-256-GCM
- RSA-2048 / RSA-OAEP
- RSA-PSS 签名与验签

### 前端

- Vue 3
- Vite
- lucide icons

### 桌面端

- Electron
- electron-builder
- Windows MSI / macOS DMG 打包入口

## 项目结构

```text
.
├── README.md              # 语言入口
├── README-ZH.md           # 中文项目总览
├── README-EN.md           # English project overview
├── Encryption_module/     # Python 加密模块和 FastAPI 加密服务
├── TCP_Module/            # Java 客户端/服务端、TCP 中继、本地 HTTP API
├── UI_Module/             # Vue 3 前端界面
├── Desktop_Module/        # Electron 桌面入口和打包配置
├── WINDOWS_PACKAGING.md   # Windows 打包说明
└── LICENSE
```

## 整体架构

```text
Vue UI / Electron Desktop
        |
        | HTTP 127.0.0.1:20201
        v
Java Client Backend
        |
        | HTTP 127.0.0.1:20202
        v
Python Crypto Service

Java Client Backend
        |
        | TCP 9000
        v
Java Relay Server
```

默认情况下，Java 客户端会连接配置中的 TCP 服务器 `82.156.228.71:9000`。如果需要完全本地部署，可以另行启动 `TCP_Module` 服务端，并准备 Redis、MySQL 和数据库表。

## 加密逻辑与实现方法

本项目的加密逻辑围绕“每次传输生成一个对称会话密钥，再用接收方公钥包装这个会话密钥”展开。在线文件块的 AES-GCM 加解密在 Java 客户端进程内完成；RSA 密钥管理、RSA-OAEP、签名和验签由本地 Python FastAPI 加密服务提供。这样 Java 传输模块可以直接处理高频数据块，同时复用独立加密服务管理密钥和非对称操作。

### 密钥体系

每个客户端通过 Python 加密服务生成和保存一组 RSA-2048 公私钥。公钥可用于联系人标识、接收方密钥包装、公钥指纹计算和验签；私钥保存在本地密钥目录中，用于签名和解开别人发给本机的加密会话密钥。

Python 加密服务提供的主要能力包括：

- 生成、导入、删除和查询 RSA 密钥对。
- 返回本机公钥和公钥状态。
- 使用 RSA-OAEP 加密/解密 AES 会话密钥。
- 使用 RSA-PSS 对认证数据或握手数据签名、验签。
- 提供 AES-GCM 接口用于算法验证和服务化调用。

### 在线文件传输加密

在线文件传输使用混合加密流程：RSA 处理会话密钥，AES-GCM 处理实际文件内容。文件内容不会作为一个整体一次性处理，而是按配置的块大小读取，默认块大小为 1MB。

```text
发送方
  1. 为本次文件传输生成 AES-256 会话密钥
  2. 使用接收方 RSA 公钥加密该会话密钥
  3. 将文件拆成多个数据块
  4. 使用 AES-GCM 加密每个文件块，得到 nonce、ciphertext、tag
  5. 发送 FileOfferPacket 和多个 FileBlockPacket

接收方
  1. 从 FileOfferPacket 中取得加密后的 AES 会话密钥
  2. 使用本机 RSA 私钥解出 AES 会话密钥
  3. 对每个 FileBlockPacket 使用 nonce、ciphertext、tag 和 AES key 解密
  4. 按 blockId 顺序重建原始文件
```

`FileOfferPacket` 保存一次传输的元信息，包括 `transferId`、发送方公钥、接收方公钥、加密后的 AES 会话密钥、文件名、文件大小和总块数。`FileBlockPacket` 保存单个文件块的 `blockId`、`nonce`、`ciphertext`、`tag` 和 `transferId`。

### 接收方解密流程

接收方收到文件请求后，先根据 `FileOfferPacket` 建立接收上下文，并使用本机私钥解开 AES 会话密钥。之后每收到一个文件块，就用 Java 进程内的 AES-GCM 解密函数还原明文块，并写入目标文件。任务状态会记录接收进度，供控制台、HTTP API 和 UI 查询。

### 身份、签名和指纹相关功能

客户端连接服务器、服务端认证、直连握手和二维码直连流程会使用签名与验签。公钥指纹也被用作账号标识和联系人识别的一部分。README 只描述这些功能在项目中的使用方式，具体接口和命令见 `TCP_Module` 文档。

### 离线文件与文本加解密

项目实现了不依赖在线传输链路的离线加解密能力。

FST2 文件载荷在结构上包含：

- magic、版本号和算法标识。
- 使用 RSA 加密后的 AES 会话密钥。
- nonce seed。
- 加密后的元数据头，包括原始文件名、文件大小、块大小和总块数。
- 多个加密数据块，包括块序号、明文长度、密文和 tag。

FST-TEXT1 文本载荷在结构上包含：

- `FST-TEXT1:` 前缀。
- Base64URL 编码后的 CBOR 载荷。
- 使用 RSA 加密后的 AES 会话密钥。
- nonce、ciphertext、tag 和明文长度。

除了文件传输，文本消息和二维码直连流程也复用同一组加密基础能力：会话密钥、AES-GCM 内容加密、RSA 包装密钥，以及签名和验签。

## 快速启动

### 环境要求

- Java 21
- Maven 3.x
- Python 3.10+
- Node.js 18+
- npm
- Redis / MySQL：仅在自建 Java 服务端时需要

### 1. 启动 Python 加密服务

```bash
cd Encryption_module
pip install -r requirements.txt
python crypto_service/main.py --host 127.0.0.1 --port 20202 --key-dir crypto_keys
```

服务启动后可访问：

```text
http://127.0.0.1:20202/health
http://127.0.0.1:20202/docs
```

### 2. 启动 Java 客户端后端

```bash
cd TCP_Module
mvn clean package
./scripts/start-client.sh
```

客户端默认启动本地 HTTP 服务：

```text
http://127.0.0.1:20201/
```

默认情况下，客户端会连接 `82.156.228.71:9000`。如需修改目标服务器，可通过 `CLIENT_SERVER_HOST` 和 `CLIENT_SERVER_PORT` 配置。

### 3. 启动 Vue 前端

```bash
cd UI_Module
npm install
npm run dev
```

开发服务器默认访问地址：

```text
http://127.0.0.1:5173/
```

Vite 已将本地 API 请求代理到 Java 客户端后端 `127.0.0.1:20201`。

### 4. 可选：启动 Java 服务端

如果希望自建完整 TCP 服务端，需要准备 Redis 和 MySQL，并执行 `TCP_Module/FileSecurityTransmission.sql` 初始化数据库。

```bash
cd TCP_Module
./scripts/start-server.sh
```

### 5. 可选：启动 Electron 桌面壳

```bash
cd Desktop_Module
npm install
npm run dev
```

最终安装包构建流程不放在根 README 中，详见 [Desktop_Module/README.md](Desktop_Module/README.md) 和 [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md)。

## 模块文档

- [Encryption_module/README.md](Encryption_module/README.md)：Python 加密模块、FastAPI 加密服务、AES-GCM 测试和性能测试。
- [TCP_Module/README.md](TCP_Module/README.md)：Java 客户端/服务端、控制台命令、本地 HTTP API、环境变量和打包脚本。
- [UI_Module/README.md](UI_Module/README.md)：Vue 前端开发、接口代理、构建和静态资源同步。
- [Desktop_Module/README.md](Desktop_Module/README.md)：Electron 开发模式、运行时准备和桌面安装包构建。
- [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md)：Windows 打包相关说明。

## 文件与密钥说明

运行过程中会生成密钥文件、本地数据库、日志、接收文件目录和构建产物。这些内容不应提交到仓库。仓库已通过 `.gitignore` 忽略常见密钥目录、PEM/KEY 文件、构建输出和本地运行数据。

默认本地 HTTP 服务绑定 `127.0.0.1`。自建服务端时，请根据实际部署环境调整数据库账号、Redis 配置、服务监听地址和端口。
