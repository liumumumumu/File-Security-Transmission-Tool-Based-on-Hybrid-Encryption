# UI / Java / Crypto 联调测试记录

本文档整理 2026-05-08 这次联调排查过程，供后续继续修改 UI 和对接后端时使用。

## 1. 当前系统结构

当前团队实际联调结构按下面理解：

```text
UI_Module
  Vue 静态前端，浏览器打开 index.html

TCP_Module
  Java Spring Boot + Netty 后端，LQH 负责

Crypto Service
  Qt/C++ + OpenSSL 本地 HTTP 加密服务
```

调用方向：

```text
Vue UI -> Java Spring Boot REST API -> Qt/C++ Crypto Service -> OpenSSL 加密能力
```

前端不直接调用 Qt crypto service，也不直接处理 TCP socket。前端只应调用 Java 后端 REST API。

## 2. 已验证事项

### Java 版本问题已解决

最初运行 `start-client.bat` 报错：

```text
UnsupportedClassVersionError
class file version 61.0
this version of the Java Runtime only recognizes class file versions up to 52.0
```

含义：

```text
52.0 = Java 8
61.0 = Java 17
```

说明当时 Windows 正在用 Java 8，不能运行 Spring Boot 3.x 打出来的 jar。

后来已确认 Java 后端使用 Java 21 启动成功：

```text
Starting FileSecurityTransmissionApplication v1.0-SNAPSHOT using Java 21.0.11
Spring Boot :: v3.3.11
Tomcat started on port 8081
```

验证命令：

```powershell
java -version
where java
javac -version
where javac
```

期望看到：

```text
java version "21..."
javac 21...
```

## 3. Java 后端启动方式

当前使用 Windows PowerShell 运行：

```powershell
cd C:\Users\15328\xwechat_files\wxid_8pzvawrubywv22_5024\msg\file\2026-05
.\scripts\start-client.bat
```

启动成功后会进入 Java 控制台：

```text
File Security Transmission console is ready.
Type 'help' for commands.
fst>
```

可以输入：

```text
help
status
key-info
generate-key
```

注意：这次日志显示客户端 HTTP 端口是：

```text
Tomcat started on port 8081
```

因此如果 UI 要直接对接这个 client 后端，页面里的 Java API 地址应填：

```text
http://127.0.0.1:8081
```

不是 `8080`。

## 4. Crypto Service 端口冲突

Java 后端启动后报：

```text
Crypto service GET failed: /key/status
Unable to check key status: Crypto service GET failed: /key/status
Run 'key-info' after confirming the crypto service is running.
```

最初以为 crypto service 未启动，后来发现根因是：

```text
9080 被 NahimicService.exe 占用
```

验证命令：

```powershell
netstat -ano | findstr :9080
tasklist /FI "PID eq <PID>"
```

当时看到：

```text
NahimicService.exe
```

`NahimicService.exe` 是厂商音效服务，通常来自声卡驱动或厂商音频增强软件，不是项目进程。

## 5. 为什么 curl 返回 301 about:blank

当时执行：

```powershell
curl.exe -v --http1.1 http://127.0.0.1:9080/key/status
```

返回：

```text
HTTP/1.1 301 Moved Permanently
Location: about:blank
```

这说明访问到的不是 Qt crypto service，而是占用 9080 的 Nahimic 本地服务。

Qt crypto service 正确返回应类似：

```text
HTTP/1.1 200 OK
Content-Type: application/json; charset=utf-8
```

并返回 JSON。

## 6. PowerShell curl 注意事项

Windows PowerShell 里：

```powershell
curl
```

通常是 `Invoke-WebRequest` 的别名，不是真正的 curl。

测试 HTTP 服务时应使用：

```powershell
curl.exe
```

例如：

```powershell
curl.exe -v --http1.1 http://127.0.0.1:9081/health
curl.exe -v --http1.1 http://127.0.0.1:9081/key/status
```

## 7. 推荐端口改法

由于 `9080` 可能被 Nahimic 占用，推荐把 Qt crypto service 改到：

```text
9081
```

### 7.1 Qt crypto service 启动参数

如果在 Qt Creator 里运行，进入：

```text
Projects / 项目
-> Run / 运行
-> Command line arguments / 命令行参数
```

填：

```text
--host 127.0.0.1 --port 9081
```

正确启动日志应包含：

```text
crypto service listening url=http://127.0.0.1:9081
```

或者至少：

```text
crypto service listening url=http://0.0.0.0:9081
```

如果仍然显示：

```text
9080
```

说明启动参数没有生效。

### 7.2 Java 后端配置 crypto-service.port

Java 后端默认会访问：

```text
http://127.0.0.1:9080
```

如果 Qt crypto service 改到 9081，Java 也必须同步改。

当前 `start-client.bat` 里有：

```bat
set "APP_ARGS=..."
```

需要在同一个引号里面追加：

```bat
--crypto-service.port=9081
```

正确形式：

```bat
set "APP_ARGS=--server.tcp.enabled=false --server.address=%CLIENT_HTTP_ADDRESS% --server.port=%CLIENT_HTTP_PORT% --node.device-id=%NODE_DEVICE_ID% --node.auto-connect=%NODE_AUTO_CONNECT% --client.serverHost=%CLIENT_SERVER_HOST% --client.serverPort=%CLIENT_SERVER_PORT% --transfer.receive-dir=%TRANSFER_RECEIVE_DIR% --crypto-service.port=9081"
```

错误形式：

```bat
set "APP_ARGS=... --transfer.receive-dir=%TRANSFER_RECEIVE_DIR%"--crypto-service.port=9081
```

错误点：`--crypto-service.port=9081` 被放在引号外面，前面也没有空格。

## 8. 推荐测试顺序

### 8.1 确认 crypto service

先启动 Qt crypto service 到 9081，然后测试：

```powershell
netstat -ano | findstr :9081
curl.exe -v --http1.1 http://127.0.0.1:9081/health
curl.exe -v --http1.1 http://127.0.0.1:9081/key/status
```

期望：

```text
LISTENING
HTTP/1.1 200 OK
JSON body
```

### 8.2 启动 Java client

确认 `start-client.bat` 已追加：

```text
--crypto-service.port=9081
```

然后运行：

```powershell
.\scripts\start-client.bat
```

进入控制台后输入：

```text
key-info
```

如果不再报：

```text
Crypto service GET failed
```

说明 Java -> Qt crypto service 已通。

### 8.3 如果没有密钥

Java 控制台输入：

```text
generate-key
key-info
```

或直接测试 crypto service：

```powershell
curl.exe -X POST http://127.0.0.1:9081/key/generate
curl.exe http://127.0.0.1:9081/key/status
```

## 9. 与 UI 的关系

UI 只对接 Java 后端，不直接对接 Qt crypto service。

当前实际端口：

```text
Java client HTTP: 8081
Qt crypto service: 9081
Netty TCP: 9000
```

因此 UI 页面中的 Java API 地址，联调 client 时应填：

```text
http://127.0.0.1:8081
```

Qt crypto service 的 9081 是 Java 内部调用端口，通常不应暴露给 UI。

## 10. 已知代码问题

在 LQH 分支 `CryptoSupport.java` 中，有两处路径疑似少了 `/`：

```java
POST("rsa/encrypt", ...)
POST("rsa/decrypt", ...)
```

建议改成：

```java
POST("/rsa/encrypt", ...)
POST("/rsa/decrypt", ...)
```

否则可能拼出非法 URL：

```text
http://127.0.0.1:9081rsa/encrypt
```

另外，若 Java `HttpClient` 仍然读 Qt HTTP 响应失败，可考虑强制 HTTP/1.1：

```java
private final HttpClient httpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .build();
```

并在请求上加：

```java
.version(HttpClient.Version.HTTP_1_1)
```

但当前主要问题已经定位为 9080 端口被 Nahimic 占用。

## 11. 新对话继续 UI 修改时的上下文

下一轮继续 UI 时，需要带上这些结论：

```text
1. UI 是 Vue CDN 静态 App Shell。
2. Java client 后端这次启动在 8081。
3. Qt crypto service 建议跑 9081，避免 Nahimic 占用 9080。
4. UI 不直接访问 9081，只访问 Java 后端。
5. start-client.bat 需要追加 --crypto-service.port=9081。
6. 后续 UI 可增加“系统状态 / 密钥状态”面板，用于调用 Java 的 /api/system/status 和 /api/system/key。
```
