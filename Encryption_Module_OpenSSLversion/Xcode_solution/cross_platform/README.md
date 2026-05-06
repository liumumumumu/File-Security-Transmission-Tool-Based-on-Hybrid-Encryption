# Cross-platform build

这个目录提供一套独立于 Xcode 的跨平台构建方式。它复用现有源码：

- `../FileSecurityTransferTool_CryptoTest/main.cpp`
- `../FileSecurityTransferTool_CryptoTest/httplib.h`

依赖由 `vcpkg.json` 声明：

- OpenSSL
- nlohmann-json

## 1. 安装工具

需要先安装：

- CMake 3.20 或更新版本
- Ninja
- vcpkg
- 一个 C++20 编译器

macOS 可以使用：

```bash
brew install cmake ninja vcpkg
```

Windows 建议使用 Visual Studio 2022 的 C++ 工具链，并安装 CMake、Ninja、vcpkg。

Linux 可以使用发行版包管理器安装 CMake、Ninja、GCC/Clang，然后安装 vcpkg。

## 2. 设置 VCPKG_ROOT

如果 `vcpkg` 不是通过 Homebrew 自动配置的，需要设置环境变量：

```bash
export VCPKG_ROOT=/path/to/vcpkg
```

Windows PowerShell：

```powershell
$env:VCPKG_ROOT="C:\path\to\vcpkg"
```

## 3. 配置和构建

在项目根目录执行：

```bash
cmake -S cross_platform --preset vcpkg-debug
cmake --build cross_platform/build/vcpkg-debug
```

Release 构建：

```bash
cmake -S cross_platform --preset vcpkg-release
cmake --build cross_platform/build/vcpkg-release
```

也可以不用 preset，手动传入 toolchain：

```bash
cmake -S cross_platform -B cross_platform/build/manual \
  -DCMAKE_BUILD_TYPE=Debug \
  -DCMAKE_TOOLCHAIN_FILE="$VCPKG_ROOT/scripts/buildsystems/vcpkg.cmake"

cmake --build cross_platform/build/manual
```

## 4. 运行

Debug preset 构建完成后，可执行文件通常在：

```bash
cross_platform/build/vcpkg-debug/crypto-service
```

运行：

```bash
./cross_platform/build/vcpkg-debug/crypto-service
```

服务启动后监听：

```text
http://127.0.0.1:9080
```

密钥仍会写入进程当前工作目录下的：

```text
./crypto_keys
```

如果从不同目录启动程序，密钥目录也会跟着改变。后续如果要打包发布，建议把密钥目录改为命令行参数或环境变量。

## 5. 平台说明

macOS、Linux、Windows 都使用同一份 `CMakeLists.txt`。

Windows 下额外链接：

- `ws2_32`
- `crypt32`

这是 `cpp-httplib` 和 OpenSSL 在 Windows 网络/证书相关能力上常见需要的系统库。

## 6. Windows 打包

在 Windows PowerShell 中执行：

```powershell
$env:VCPKG_ROOT="C:\vcpkg"
.\cross_platform\package-windows.ps1
```

输出文件：

```text
dist\crypto-service-windows-x64.zip
```

包内包含：

- `crypto-service.exe`
- `libssl-3-x64.dll`
- `libcrypto-3-x64.dll`
- `crypto_keys\`
