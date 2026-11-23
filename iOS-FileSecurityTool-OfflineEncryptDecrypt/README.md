# File Security for iPhone

原生 SwiftUI 离线文件与文本加解密工具，基于本仓库 Android 端协议移植。最低 iOS 17，建议使用 Xcode 16 或更新版本（本次使用 Xcode 26.2 验证）。无第三方依赖，无服务端配置。

## 用 Xcode 打开

1. 打开本目录的 **FileSecurity.xcodeproj**，选择 **FileSecurity** scheme。
2. 选择 iPhone 模拟器即可运行；真机运行时，在 Signing & Capabilities 中选择自己的 Development Team，并按需修改 Bundle Identifier。
3. 真机设置设备密码后，进入“本机密钥”创建 RSA 密钥，再导入联系人公钥或使用自己的公钥测试。
4. 分享公钥或二维码给 Android / Java 客户端，核对 SHA-256 指纹后双向测试文件与文本。

模拟器适合验证界面、联系人、格式兼容和错误处理。设备密码、Face ID / Touch ID、摄像头扫描、文件提供程序和系统文件关联必须继续在真机验证。项目不包含开发者账号、签名证书或描述文件。

## 功能与使用

- **工作台**：文件 / 文本两种模式；选择本机或联系人公钥加密；使用本机私钥解密。
- **文件**：从系统“文件”选择输入，1 MiB 流式分块，显示进度并支持取消。加密输出使用 UUID.FST2 隐藏原文件名；认证解密后恢复并净化原文件名。
- **结果导出**：通过系统分享面板“存储到文件”或分享给其他 App。结果暂存在 App 私有临时目录；下次启动或手动清理时删除，需自行保存。原文件保持不变。
- **文件接收**：注册 FST2 类型，可从“文件”中选择使用本 App 打开；其他 App 是否显示此入口取决于其对系统文档交互的支持。
- **文本**：上限为 16 KiB UTF-8；粘贴 FST-TEXT1 密文解密，结果可复制、分享。复制结果仅在本设备有效，120 秒后过期。
- **联系人**：名称搜索、删除、PEM / Base64 DER / FST-PUB1 / FST-KEY1 JSON 导入；支持文件、相机扫码和照片二维码识别。系统照片选择器无需整库权限。
- **本机密钥**：RSA-2048，分享 PEM / FST-PUB1 公钥和二维码，查看指纹，设备认证后删除密钥。
- **设置**：中英文、浅色 / 深色 / 跟随系统、临时文件清理。

此版通过“文件”打开 FST2，通过粘贴接收加密文本，没有独立的 iOS Share Extension。iOS 文件任务限前台执行，进入后台会请求取消并清理未完成文件，不提供 Android 前台服务式后台执行。

## 密钥与数据

RSA 私钥由 Apple Security.framework 创建并保存在 Keychain，采用 `WhenPasscodeSetThisDeviceOnly` 和 `userPresence`。使用私钥需要系统设备认证。RSA 不使用 Secure Enclave（Secure Enclave 不提供 RSA）；不声称硬件 RSA 加密。私钥不能通过本 App 导出，不会随备份迁移。移除设备密码、抹除设备或删除密钥后，历史密文可能永久无法恢复，请在使用前明确接受这一限制。卸载后 Keychain 项可能仍保留；重装后可认证恢复公钥显示。

联系人和公钥缓存位于 Application Support，并设置完整文件保护、排除备份。文件任务使用完整保护的私有临时目录；失败或取消后删除未完成结果。进入后台会清除界面中的文本内容。App 自身不发起网络请求；选择 iCloud 等提供程序的文件时，系统可能自行联网下载。

图标逐字节复用 Android 模块的 `assets/FileSecurityTransmission.iconset/icon_512x512@2x.png`，未重新绘制。

## 格式

- `FST2` v1：RSA-OAEP SHA-256 / MGF1-SHA256 封装 AES-256 会话密钥。
- AES-256-GCM，12 字节 nonce，16 字节认证标签。
- 32 字节种子，经 HMAC-SHA256 派生每块 nonce，域分离为 `FST2-header` / `FST2-block`。
- canonical CBOR、big-endian 长度前缀、认证文件头及块索引 / 长度。
- `FST-TEXT1:` + Base64URL CBOR；指纹为规范 PEM 文本的 SHA-256，与 Android 一致。
- 严格拒绝超限字段、不支持算法、重复 CBOR 字段、截断、篡改、额外尾部及不合法 UTF-8。

## 模块

```text
FileSecurity/
  Core/       # 可独立测试的 CBOR、RSA 公钥、FST2 与 FST-TEXT1
  App/        # App 入口、Keychain、本地存储与任务协调
  Features/   # 工作台、联系人、二维码、本机密钥、设置
  Resources/  # Android 原图标、中英文资源、Info.plist
Tests/        # Java/Android 固定公开测试向量与密码协议测试
UITests/      # iPhone 导航、语言与输入校验冒烟测试
```

`Tests/Vectors.json` 中的私钥仅为 Android 测试套件已有的公开测试夹具，不能用于真实数据。

## 验证命令

```bash
swift test
xcodebuild -project FileSecurity.xcodeproj -scheme FileSecurity \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
# 换成当前机器的模拟器名称 / ID
xcodebuild -project FileSecurity.xcodeproj -scheme FileSecurity \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' CODE_SIGNING_ALLOWED=NO test
```

测试涵盖 Java → iOS 与 Android → iOS 固定密文解密、iOS 文本 / 文件往返、空文件与 1 MiB 边界、损坏容器清理、恶意 CBOR 和公钥格式转换。Xcode scheme 的 Test 操作运行 UI 测试，协议单元测试通过上述 `swift test` 运行。

## 真机验收

- 在 iPhone 与 Android / Java 间进行文件和文本双向互操作，覆盖中文文件名、空文件、大文件与错误接收者。
- 验证 Face ID / Touch ID / 设备密码、拒绝认证、删除密钥警告与恢复公钥显示。
- 验证相机扫码、图片导入、拒绝相机权限以及不同文件提供程序。
- 验证导出到“文件”、从其他 App 打开 FST2、取消任务、后台切换与重启后清理。

这是待继续真机验证的开发版本，尚未完成独立密码安全审计。
