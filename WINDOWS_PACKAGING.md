# Windows 本机打包 MSI 安装程序说明

本仓库的 Windows 桌面客户端，最终以 **Electron MSI 安装包** 的形式交付。

最终安装包包含：

- `Desktop_Module` 的 Electron 桌面壳
- `TCP_Module` 产出的 Spring Boot 客户端 jar
- `Encryption_Module_OpenSSLversion` 产出的本地加密服务
- 来自 `JAVA_HOME` 的内置 Java 运行时

它**不包含**远程中心服务端（MySQL / Redis / 服务端 TCP 中继）。

---

## 1. 适用环境

请在 **Windows 本机 PowerShell** 中执行以下流程：

- 仓库根目录：`d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption`
- 不要在 WSL 中执行 Electron 桌面预览或 MSI 打包

---

## 2. 需要的工具链

建议先执行仓库里的检查脚本：

```powershell
.\scripts\check-windows-prereqs.ps1
```

必备工具：

- Java 21 JDK
- Maven 3.9+
- Node.js 22+
- CMake 3.28+
- Visual Studio 2022 C++ Build Tools
- WiX Toolset
- vcpkg，并正确设置 `VCPKG_ROOT`

推荐工具：

- Git
- 7-Zip

---

## 3. 先看桌面程序长什么样

如果你只是想快速预览 Electron 桌面窗口外观，不需要先手动启动 Java 客户端。

先启动前端开发服务器：

```powershell
cd d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\UI_Module
npm install
npm run dev
```

再开一个新的 Windows PowerShell，启动 Electron：

```powershell
cd d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\Desktop_Module
npm install
npm run dev
```

默认会打开：

```text
http://127.0.0.1:5173/
```

这个模式适合查看：

- 窗口标题栏
- 窗口图标
- 侧边栏 / 设置抽屉
- 输入框、按钮、图标等桌面 UI 外观

---

## 4. 预览“更接近成品”的桌面程序

如果你希望 Electron 加载的是 **本地 Java 客户端页面**，而不是 Vite 开发服务器页面，那么需要先手动启动 Java 客户端。

### 4.1 先准备前端静态资源和 Java 包

```powershell
cd d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\UI_Module
npm install
npm run build:static
```

```powershell
cd ..\TCP_Module
mvn clean package
```

### 4.2 再启动本地 Java 客户端

用你本地现有的 Windows 启动脚本，或者直接运行 jar，让它监听：

```text
http://127.0.0.1:20201/
```

### 4.3 最后启动 Electron，并指向本地 Java 页面

```powershell
cd ..\Desktop_Module
npm install
$env:DESKTOP_RENDERER_URL="http://127.0.0.1:20201/"
npm run dev
```

这个模式下：

- Electron 仍然是开发态窗口
- 但页面内容来自本地 Java client
- 适合验证 Electron 壳 + 本地 Java 页面联调

---

## 5. 正式构建 MSI 安装包

正式 MSI 打包前，需要先把 Java 和 crypto 的运行时产物构建出来。

注意：

- 这里不是“先长期手动运行 Java 脚本”
- 而是**先把 jar 和 crypto runtime 产物构建出来**
- MSI 安装后的桌面程序会自己拉起 bundled Java 和 crypto

### 5.1 构建 UI 静态资源

```powershell
cd d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\UI_Module
npm install
npm run build:static
```

这一步会把最新前端产物同步到：

```text
TCP_Module\src\main\resources\static\
```

### 5.2 构建 Java 客户端 jar

```powershell
cd ..\TCP_Module
mvn clean package
```

期望产物：

```text
TCP_Module\target\FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar
```

### 5.3 构建 Windows crypto runtime

```powershell
cd ..\Encryption_Module_OpenSSLversion\Xcode_solution\deploy
.\package-windows.ps1
```

期望目录：

```text
Encryption_Module_OpenSSLversion\Xcode_solution\dist\crypto-service-windows-x64\
```

### 5.4 打包 Electron MSI

```powershell
cd ..\..\..\Desktop_Module
npm install
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
npm run dist:win-msi
```

期望输出：

```text
Desktop_Module\release\*.msi
```

---

## 6. 安装后的运行逻辑

安装 MSI 后，终端用户**不需要手动启动 Java 或 crypto**。

预期行为：

1. 双击桌面图标
2. Electron 启动
3. Electron 自动创建本地可写目录：
   - `%LOCALAPPDATA%\FileSecurityTransmission\`
   - `%LOCALAPPDATA%\FileSecurityTransmission\crypto_keys\`
   - `%LOCALAPPDATA%\FileSecurityTransmission\logs\`
   - `%USERPROFILE%\Downloads\FileSecurityTransmission\`
4. Electron 自动启动：
   - `crypto-service.exe`
   - Java client jar
5. 等待：
   - `http://127.0.0.1:20201/api/system/status`
6. 本地服务就绪后加载：
   - `http://127.0.0.1:20201/`
7. 退出 Electron 时，自动清理本轮拉起的 Java / crypto 子进程

---

## 7. 运行时注入的环境变量

打包后的桌面程序会注入这些环境变量：

- `CRYPTO_SERVICE_ADDRESS=127.0.0.1`
- `CRYPTO_SERVICE_PORT=20202`
- `CRYPTO_SERVICE_KEY_DIR=%LOCALAPPDATA%\FileSecurityTransmission\crypto_keys`
- `CLIENT_HTTP_ADDRESS=127.0.0.1`
- `CLIENT_HTTP_PORT=20201`
- `TRANSFER_RECEIVE_DIR=%USERPROFILE%\Downloads\FileSecurityTransmission`
- `NODE_AUTO_CONNECT=true`
- `CLIENT_SERVER_HOST=82.156.228.71`
- `CLIENT_SERVER_PORT=9000`
- `APP_UI_OPEN_BROWSER=false`

---

## 8. 什么时候需要手动启动 Java

这个问题分三种情况：

### 只看桌面壳

不需要。

也就是：

- `UI_Module/npm run dev`
- `Desktop_Module/npm run dev`

这种只看 Electron + Vite 页面外观的方式，不需要你手动运行 Java。

### 看 Electron + 本地 Java 页面联调

需要。

你要先手动启动本地 Java client，让它监听 `127.0.0.1:20201`，然后让 Electron 用：

```powershell
$env:DESKTOP_RENDERER_URL="http://127.0.0.1:20201/"
npm run dev
```

### 正式 MSI

不需要终端用户手动启动。

只需要你在打包前先构建出：

- Java jar
- crypto runtime

安装后的 Electron 会自己拉起它们。

---

## 9. 常见问题

### `mvn` 无法识别

说明 Maven 没装好，或者 PATH 没生效。安装 Maven 后，重新打开 PowerShell 再试。

### `cl` 无法识别

说明 Visual Studio 2022 C++ Build Tools 没准备好。建议用 VS 开发者命令行，或者把对应环境加到 PATH。

### `vcpkg` 失败

检查 `VCPKG_ROOT` 是否正确指向 vcpkg 根目录。

### `npm run dist:win-msi` 报 `JAVA_HOME` 缺失

请先设置：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
```

然后重新执行打包命令。

### WiX 相关报错

先确认以下命令能在当前 PowerShell 正常执行：

```powershell
wix --version
```

如果不行，说明 WiX 没装好，或者 PATH 没生效。

---

## 10. 推荐的最短打包命令清单

如果你只想要一套最短、能直接复制执行的打包流程，就按下面来：

```powershell
cd d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\UI_Module
npm install
npm run build:static

cd ..\TCP_Module
mvn clean package

cd ..\Encryption_Module_OpenSSLversion\Xcode_solution\deploy
.\package-windows.ps1

cd ..\..\..\Desktop_Module
npm install
$env:JAVA_HOME="C:\Program Files\Java\jdk-21"
npm run dist:win-msi
```

最终 MSI 在：

```text
d:\MF_xian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\Desktop_Module\release\
```
