# Handoff Document — FileEnDecryptionTool Android App

**Date:** 2026-07-03
**Project:** FileEnDecryptionTool (Android 加密解密工具)
**Branch:** `main`

---

## 1. 项目目标

将现有的 Kotlin/JVM 项目改造为 **Android App**，实现一个纯本地的文件/文本加密解密工具。核心需求：

- **不需要连接服务器**（与参考 Java 的 TCP_Module 不同）
- 具备**联系人系统**管理联系人的公钥
- 支持**加密文件**和**加密文本信息**
- 加密后的内容放入**容器格式**（FST2 / FST-TEXT1）
- 界面风格参考 **Swift 实现的 macOS App**
- 提供**在文件夹中打开**加密/解密结果的功能

---

## 2. 当前代码库状态

### 2.1 项目结构（目前是 Kotlin/JVM，非 Android）

```
FileEnDecryptionTool/
├── app/
│   ├── build.gradle.kts          # JVM application 插件，依赖 :utils
│   └── src/main/kotlin/App.kt    # Hello World 占位代码
├── utils/
│   ├── build.gradle.kts
│   └── src/main/kotlin/Utilities.kt
├── buildSrc/
│   └── src/main/kotlin/kotlin-jvm.gradle.kts  # JVM 约定插件 (Java 21)
├── gradle/libs.versions.toml     # 版本目录 (Kotlin 2.2.20)
├── settings.gradle.kts           # 包含 :app 和 :utils
└── gradle.properties
```

**关键发现：当前项目是纯 JVM 项目，需要迁移到 Android SDK + Jetpack Compose。**

### 2.2 参考代码

| 参考 | 路径 | 用途 |
|------|------|------|
| Java TCP 模块 | `参考/TCP_Module/` | 加密服务(`OfflineCryptoService`)、容器格式、联系人管理、密钥管理 |
| Swift macOS App | `参考/MacOS Desktop_Module/` | UI 设计参考（OfflineView、分段选择器、拖放区域、结果显示面板） |

---

## 3. 从参考代码中提炼的核心设计

### 3.1 容器格式（必须兼容）

**FST2 文件格式**（参考：`OfflineCryptoService.java:48-53`）：
- Magic: `FST2` (4 bytes)
- Version: `1` (1 byte)
- 算法标识: RSA key wrap(1) + AES-256-GCM(1) + HMAC-SHA256 nonce(1)
- 结构: `[prefix] [len][encryptedSessionKey] [len][nonceSeed] [len][headerCiphertext] [len][headerTag] [blocks...]`
- 每个 block: `[blockIndex:int] [plaintextLen:int] [ciphertextLen:int] [ciphertext] [len][tag]`
- Header 是 CBOR 编码，包含 fileName, fileSize, chunkSizeBytes, totalBlocks

**FST-TEXT1 文本格式**（参考：`OfflineCryptoService.java:57`）：
- 前缀: `FST-TEXT1:`
- Payload: Base64URL 编码的 CBOR 数据
- 内容: version, keyWrapAlg, contentAlg, encryptedSessionKey, nonce, ciphertext, tag, plaintextLength

**密码学参数**（参考：`CryptoSupport.java`）：
- RSA-2048, RSA-OAEP(SHA-256) 用于 AES 密钥封装
- AES-256-GCM 用于内容加密
- HMAC-SHA256 用于 nonce 派生
- RSA-PSS(SHA-256) 用于签名/验签
- 公钥指纹: SHA-256

### 3.2 Swift UI 设计（界面参考）

参考 `OfflineView.swift`，核心 UI 结构：
- **顶部分段选择器**: 文件加密 / 文件解密 / 文本加密 / 文本解密
- **文件加密页**: 左侧拖放区域 + 输出目录选择 + 结果面板 / 右侧联系人选择器 + 加密按钮
- **文件解密页**: 左侧输入区域 + 输出目录 / 右侧解密按钮
- **文本加解密页**: 左右两个文本编辑器 + 操作按钮
- **结果面板**: 显示文件名、大小、块数、输出路径 + 复制路径按钮 + **Reveal in Finder**（Android 对应 → 在文件管理器中打开）
- **联系人来源**: 已存联系人 / 粘贴公钥文本 / 选择公钥文件

### 3.3 联系人系统（参考 Java 模块）

参考 `LocalContactBookService.java` 和 `ContactRecord.java`：
- 联系人存储: accountId, alias (显示名), publicKey, 创建/更新时间
- 本地 SQLite 存储
- 黑名单功能

### 3.4 密钥管理

参考 `CryptoSupport.java`:
- RSA-2048 密钥对生成
- 私钥 PBKDF2+AES-256-GCM 加密存储
- 公钥 PEM 格式存储
- 支持导入/导出/删除密钥

---

## 4. 已确认的设计决策（2026-07-03 访谈结果）

| # | 问题 | 决策 |
|---|------|------|
| 1 | UI 框架 | **Jetpack Compose** |
| 2 | 容器格式兼容性 | **完全兼容** Java 参考代码的 FST2/FST-TEXT1，逻辑/结构/扩展名全一致 |
| 3 | 联系人存储 | **Room (SQLite)** |
| 3a | 密钥推导 | **由私钥推导公钥**（RSA CRT 天然支持） |
| 4 | 密钥存储安全 | **Android Keystore**（硬件级 TEE/StrongBox），只有本 App 能访问 |
| 5 | 加密算法实现 | **混合方案**：RSA 密钥 + 存储用 Android Keystore；AES-GCM/CBOR/HMAC 逻辑从 Java 精确移植到 Kotlin |
| 6 | 最低 API Level | **API 31 (Android 12)** |
| 7 | 默认输出目录 | **`Downloads/FileSecurity/`** |
| 8 | "在文件夹中打开" | **系统文件管理器定位**（Intent + FileProvider） |
| 9 | 多语言 | **跟随系统 + 配置文件保存用户选择**（至少中/英） |
| 10 | 架构模式 | **MVVM** (ViewModel + StateFlow + Compose) |
| 11 | CBOR 编解码 | **直接移植 `CborLite.java` 到 Kotlin**（保证规范化编码兼容） |
| 12 | 公钥来源方式 | **三种都支持**（联系人选择 / 粘贴公钥文本 / 选择公钥文件） |

---

## 5. 下一步工作（按优先级排序）

1. **重构项目为 Android 项目**：修改 `build.gradle.kts`，引入 Android Gradle Plugin + Jetpack Compose + Room，target API 31+
2. **移植密码学核心到 Kotlin**：
   - 移植 `CborLite.java` → `CborLite.kt`
   - 移植 `OfflineCryptoService.java` → `OfflineCryptoService.kt`（AES-GCM 块加解密 + HMAC nonce 派生 + FST2/FST-TEXT1 容器）
   - 实现 `CryptoKeystore.kt`（RSA 密钥生成/存储/操作通过 Android Keystore）
3. **实现数据层**：Room 数据库（Contact、ContactDao、AppDatabase）
4. **实现 ViewModels**：KeyViewModel、ContactViewModel、EncryptViewModel、DecryptViewModel
5. **实现 UI 层**（参考 Swift OfflineView）：
   - 主界面：四段选择器（文件加密/解密、文本加密/解密）
   - 文件加密页：拖放/选择文件 + 输出目录 + 接收者来源（联系人/粘贴公钥/公钥文件）+ 结果面板
   - 文件解密页：选择 FST2 文件 + 输出目录 + 结果面板
   - 文本加解密页：双文本编辑器 + 操作按钮
   - 联系人管理页：添加/删除/查看联系人
   - 密钥管理页：生成/导入/导出/删除密钥
   - 结果面板含"在文件夹中打开"按钮
6. **实现"在文件夹中打开"**：`Intent` + `FileProvider`
7. **多语言资源**：`strings.xml` (中文默认 + 英文)

---

## 6. 建议下一个会话使用的技能

- **`/init`**: 初始化 CLAUDE.md 项目文档
- **`/review`**: 在提交前 review 代码变更

---

## 7. 参考文件索引

| 内容 | 路径 |
|------|------|
| FST2/FST-TEXT1 容器格式实现 | `参考/TCP_Module/src/main/java/com/client/service/OfflineCryptoService.java` |
| RSA/AES-GCM 密码学核心 | `参考/TCP_Module/src/main/java/com/crypto/CryptoSupport.java` |
| 联系人存储模型 | `参考/TCP_Module/src/main/java/com/persistence/local/model/contactsRecord/ContactRecord.java` |
| 联系人服务 | `参考/TCP_Module/src/main/java/com/client/service/LocalContactBookService.java` |
| CBOR 编解码 | `参考/TCP_Module/src/main/java/com/client/direct/qr/CborLite.java` |
| Swift UI - 离线加解密视图 | `参考/MacOS Desktop_Module/FileSecurityTransmission-Offline/Offline/OfflineView.swift` |
| Swift UI - ViewModel | `参考/MacOS Desktop_Module/FileSecurityTransmission-Offline/AppShell/AppViewModel.swift` |
| Java 模块 README | `参考/TCP_Module/README.md` |
| 当前项目 build 配置 | `app/build.gradle.kts`, `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts` |
| 版本目录 | `gradle/libs.versions.toml` |

---

## 8. 会话状态

✅ **已完成**：代码库探索、参考代码分析、12 项设计决策访谈，全部已确认并记录在 Section 4。

➡️ **下一步**：按 Section 5 的优先级开始实现。第一个动作是重构项目为 Android 项目 + Jetpack Compose。

⚠️ **关键约束**：
- 用户要求一切与 Java 参考代码完全兼容（容器格式、文件扩展名、算法参数）
- 密钥安全必须使用 Android Keystore 硬件保护
- Handoff 文档放在 `handOff/` 文件夹下
- 使用中文交流
