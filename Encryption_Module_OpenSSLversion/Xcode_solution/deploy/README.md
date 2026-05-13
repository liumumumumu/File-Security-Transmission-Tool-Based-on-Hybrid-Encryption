# Crypto Service 一键部署

这个目录用于生成不同操作系统的可部署包。最终用户不需要安装 CMake、vcpkg 或 OpenSSL，只需要下载对应系统的压缩包并运行启动脚本。

## 产物结构

打包后目录类似：

```text
crypto-service-<platform>/
├── crypto-service              # macOS/Linux 主程序
├── crypto-service.exe          # Windows 主程序
├── libssl / libcrypto          # 动态库，按平台存在
├── crypto_keys/                # 默认密钥目录
├── start.sh                    # macOS/Linux 一键启动
├── start.bat                   # Windows 一键启动
├── install-linux-systemd.sh    # Linux 安装为 systemd 服务
├── install-macos-launchd.sh    # macOS 安装为 launchd 服务
├── install-windows-service.ps1 # Windows 安装为系统服务
└── README.md
```

## 打包前要求

打包机器需要安装：

- CMake
- vcpkg
- 对应平台的 C++ 编译器
- Windows 需要 Visual Studio 2022 C++ 工具链

设置 `VCPKG_ROOT`：

```bash
export VCPKG_ROOT=/path/to/vcpkg
```

Windows PowerShell：

```powershell
$env:VCPKG_ROOT="C:\vcpkg"
```

## 生成部署包

Linux：

```bash
./deploy/package-linux.sh
```

输出：

```text
dist/crypto-service-linux-x64.tar.gz
```

macOS：

```bash
./deploy/package-macos.sh
```

输出：

```text
dist/crypto-service-macos-arm64.zip
```

如果需要在 Intel macOS 上打包：

```bash
CRYPTO_SERVICE_ARCH=x64 ./deploy/package-macos.sh
```

Windows PowerShell：

```powershell
.\deploy\package-windows.ps1
```

输出：

```text
dist\crypto-service-windows-x64.zip
```

## 一键启动

macOS/Linux：

```bash
./start.sh
```

Windows：

```bat
start.bat
```

默认监听：

```text
http://0.0.0.0:9080
```

健康检查：

```bash
curl http://127.0.0.1:9080/health
```

## 启动配置

启动脚本支持通过环境变量调整：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `CRYPTO_SERVICE_HOST` | `0.0.0.0` | 监听地址 |
| `CRYPTO_SERVICE_PORT` | `9080` | 监听端口 |
| `CRYPTO_SERVICE_KEY_DIR` | 包目录下的 `crypto_keys` | 密钥目录 |

示例：

```bash
CRYPTO_SERVICE_HOST=127.0.0.1 CRYPTO_SERVICE_PORT=19080 ./start.sh
```

Windows PowerShell：

```powershell
$env:CRYPTO_SERVICE_HOST="127.0.0.1"
$env:CRYPTO_SERVICE_PORT="19080"
.\start.bat
```

## 安装为系统服务

Linux systemd：

```bash
./install-linux-systemd.sh
```

macOS launchd：

```bash
./install-macos-launchd.sh
```

Windows PowerShell 以管理员运行：

```powershell
.\install-windows-service.ps1
```

服务安装脚本也支持环境变量或参数调整端口、安装目录。默认会把程序安装到系统目录，并把密钥放到系统数据目录：

| 系统 | 程序目录 | 密钥目录 |
| --- | --- | --- |
| Linux | `/opt/crypto-service` | `/var/lib/crypto-service/crypto_keys` |
| macOS | `/usr/local/crypto-service` | `/usr/local/var/crypto-service/crypto_keys` |
| Windows | `%ProgramFiles%\CryptoService` | `%ProgramData%\CryptoService\crypto_keys` |

## 程序参数

主程序本身也支持直接指定参数：

```bash
./crypto-service --host 0.0.0.0 --port 9080 --key-dir ./crypto_keys
```

查看帮助：

```bash
./crypto-service --help
```
