# FileSecurityTransmission for Android

献给我的朋友们

[简体中文](README.md) | [English](README_EN.md)

FileSecurityTransmission 是一个纯本地 Android 文件与文本加解密工具。应用不连接业务服务器，通过联系人公钥加密内容，并生成与 macOS/Java 客户端兼容的 `FST2` 文件或 `FST-TEXT1` 文本。

> 当前项目仍处于开发和跨设备验证阶段，不应作为已审计的正式密码产品发布。

## 功能

- 文件加密与解密，输出到 `Downloads/FileSecurity/`
- 文本加密与解密
- FST2 / FST-TEXT1 跨平台容器
- 本地联系人及公钥管理
- 扫描、导入和导出公钥二维码
- 本机公钥作为加密接收者
- 文件任务进度、取消、后台前台服务与失败清理
- 中英文界面、浅色/深色/跟随系统主题
- 接收系统分享的 FST-TEXT1 文本及 FST2 文件
- 生物识别或设备凭据保护私钥使用

应用完全离线运行。摄像头权限仅在扫描二维码时申请，通知权限仅在需要显示后台文件任务时申请。

## 兼容格式

### FST2 文件

FST2 使用分块 AES-256-GCM 加密，文件头保存经过认证的原始文件名、文件大小、块大小和块数。每个块使用独立派生的 nonce，并对块序号及长度进行认证。

加密容器采用与 Java 客户端相同的 `<UUID>.FST2` 随机名称，不暴露原文件名。只有解密并认证文件头后，应用才恢复原文件名。

### FST-TEXT1 文本

文本密文格式为：

```text
FST-TEXT1:<Base64URL 编码的 CBOR 载荷>
```

单条明文最大为 16 KiB。

### 密码学参数

- RSA-2048
- RSA-OAEP：SHA-256，MGF1-SHA256
- AES-256-GCM：128 位认证标签
- HMAC-SHA256：FST2 nonce 派生
- SHA-256：公钥指纹

协议基准实现位于：

```text
/Users/zero/Documents/c_idea_code/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule_copy
```

## 密钥安全模型

Android 12–14 的硬件 RSA Keystore 无法可靠执行协议要求的 MGF1-SHA256。为保持与 Mac/Java 端一致，应用采用以下方案：

1. 在标准密码 Provider 中生成 RSA-2048 密钥对。
2. 使用 Android Keystore 中的硬件 AES-256-GCM 密钥加密 PKCS#8 私钥。
3. AES 包装密钥优先使用 StrongBox，不可用时回退到 TEE。
4. 解密包装后的私钥需要强生物识别或设备凭据，授权窗口为 5 分钟。
5. 应用备份与系统数据迁移已禁用。

旧版应用生成的不可导出硬件 RSA 私钥无法迁移到该方案。升级后必须删除旧密钥、生成新密钥并重新分发公钥。删除密钥会导致使用对应旧公钥生成的历史密文永久无法解密。

## 系统要求

- Android 12 / API 31 或更高版本
- 已配置安全锁屏，生成和使用私钥时需要
- Android SDK 35
- JDK 21

项目为单一 `app` 模块，包名为：

```text
com.filesecuritytool.android
```

## 构建

使用仓库内的 Gradle Wrapper：

```bash
./gradlew assembleDebug
```

Debug APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

连接设备后可安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

本项目不包含正式签名密钥，也不应将 `.jks`、`.keystore` 或签名配置提交到 Git。

## 测试与检查

运行 JVM 单元测试：

```bash
./gradlew testDebugUnitTest
```

运行静态检查：

```bash
./gradlew lintDebug
```

构建并执行主要本地检查：

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

连接 Android 设备或模拟器后运行 instrumentation 测试：

```bash
./gradlew connectedDebugAndroidTest
```

JVM 测试覆盖容器往返、损坏输入、分块边界、短读文件流、Java 互操作向量、公钥载荷兼容、Room 联系人逻辑和输出文件名处理。最终发布前仍必须在 Android 12–15 真机完成 Mac↔Android 文本与文件双向测试。

## 项目结构

```text
app/src/main/kotlin/com/app/
├── core/
│   ├── crypto/       # 密钥保护、OAEP、公钥和应用密码入口
│   └── files/        # Downloads/MediaStore 输出
├── crypto/           # FST2、FST-TEXT1、AES-GCM、CBOR
├── data/             # Room 联系人和 DataStore 设置
├── feature/          # 各页面 ViewModel
├── service/          # 后台文件任务
└── ui/               # Compose 页面、二维码和主题
```

主要入口：

- `core/crypto/HardwareKeyStore.kt`：RSA 私钥的硬件 AES 包装
- `core/crypto/RsaOaep.kt`：固定 SHA-256/MGF1-SHA256 的 RSA-OAEP
- `crypto/OfflineCryptoService.kt`：FST2 与 FST-TEXT1 容器
- `service/FileTaskCoordinator.kt`：单文件任务协调和结果清理
- `MainActivity.kt`：Compose 导航、权限与设备认证

## 数据与输出

- 联系人：本地 Room 数据库
- 设置：本地 DataStore
- 密钥材料：应用私有存储中的加密私钥及 Android Keystore 包装密钥
- 加密、解密结果：`Downloads/FileSecurity/`
- 公钥二维码：`Downloads/FileSecurity/`

输入文件始终保留。任务取消或失败时，未完成的 MediaStore 输出会被删除。解密结果发生重名时自动添加 `(1)`、`(2)` 等后缀。

## 开发约束

- 不添加网络业务依赖或服务器通信。
- 不改变 FST2/FST-TEXT1 格式而不同时更新跨平台兼容测试。
- 不依赖 Provider 的 OAEP 默认参数，必须显式指定 SHA-256 和 MGF1-SHA256。
- 不提交本地 SDK 路径、构建产物、签名密钥或私密配置。
- Room schema 位于 `app/schemas/`，需要纳入版本控制。

完整设计决策和实现记录见 [`handOff/`](handOff/)。
