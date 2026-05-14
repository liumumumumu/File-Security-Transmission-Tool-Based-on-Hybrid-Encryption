# File Security Transmission Tool - TCP Module

[English README](README_EN.md)

基于 Java / Spring Boot / Netty 的文件安全传输模块。该模块同时包含客户端和服务端入口，通过同一个可执行 jar 使用不同 role 启动。

客户端提供两类入口：

- 交互式控制台，用于连接、发送文件、查看任务、管理联系人和密钥。
- 本地 HTTP API，默认监听 `127.0.0.1:20201`，用于被其他本地程序或前端界面调用。

服务端提供 TCP 中继能力，负责认证、在线设备路由、文件传输请求转发、取消和断点重传相关协议转发。

## 技术栈

- Java 21
- Spring Boot 3.3.11
- Netty 4.1.119
- MyBatis
- Redis
- MySQL
- SQLite
- Maven

## 项目结构

```text
src/main/java/com/client       客户端连接、控制台、HTTP API、文件传输业务
src/main/java/com/server       服务端 TCP 中继、认证、路由和持久化
src/main/java/com/common       公共协议、编解码、配置和工具类
src/main/java/com/session      传输任务状态模型
src/main/resources             Spring Boot 配置
scripts                        macOS/Linux/Windows 启动脚本
```

## 默认端口和地址

客户端默认配置：

```text
TCP 服务器地址: 82.156.228.71:9000
自动连接服务器: true
客户端 HTTP:   127.0.0.1:20201
加密服务地址:  127.0.0.1:20202
接收文件目录:  downloads-client-1
```

服务端默认配置：

```text
服务端 TCP: 0.0.0.0:9000
服务端 HTTP: 0.0.0.0:8080
Redis: 127.0.0.1:6379
MySQL: 127.0.0.1:3306/db_FileSecurityTransmission
```

## 构建

```bash
mvn clean package
```

生成的 jar 默认位于：

```text
target/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar
```

## 启动客户端

macOS / Linux：

```bash
./scripts/start-client.sh
```

Windows：

```bat
scripts\start-client.bat
```

客户端启动后会自动连接默认服务器 `82.156.228.71:9000`，并同时启动本地 HTTP 服务 `127.0.0.1:20201`。

也可以直接使用 jar 启动：

```bash
java -jar target/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar --app.role=client --spring.profiles.active=client
```

## 启动服务端

macOS / Linux：

```bash
./scripts/start-server.sh
```

Windows：

```bat
scripts\start-server.bat
```

服务端启动前需要准备 Redis 和 MySQL，并执行 `FileSecurityTransmission.sql` 初始化数据库表。

## 常用环境变量

客户端：

```text
CLIENT_SERVER_HOST      默认 82.156.228.71
CLIENT_SERVER_PORT      默认 9000
NODE_AUTO_CONNECT       默认 true
CLIENT_HTTP_ADDRESS     默认 127.0.0.1
CLIENT_HTTP_PORT        默认 20201
TRANSFER_RECEIVE_DIR    默认 downloads-client-1
NODE_DEVICE_ID          可选；不设置时客户端会生成并保存本机 deviceId
```

服务端：

```text
SERVER_TCP_BIND_HOST    默认 0.0.0.0
SERVER_TCP_BIND_PORT    默认 9000
SERVER_HTTP_ADDRESS     默认 0.0.0.0
SERVER_HTTP_PORT        默认 8080
REDIS_HOST              默认 127.0.0.1
REDIS_PORT              默认 6379
MYSQL_HOST              默认 127.0.0.1
MYSQL_PORT              默认 3306
MYSQL_DATABASE          默认 db_FileSecurityTransmission
MYSQL_USERNAME          默认 root
MYSQL_PASSWORD          默认 123456zxc@
```

## 控制台命令

启动客户端后可在 `fst>` 提示符下输入命令。

```text
help                                      查看帮助
language                                  切换 help 说明界面语言
status                                    查看客户端连接状态
connect [host] [port]                     连接并认证服务器
disconnect                                断开服务器连接
send <filePath> <targetAccountId>         发送文件
incoming                                  查看待处理接收请求
accept <transferId>                       接收传输请求
reject <transferId>                       拒绝传输请求
cancel <taskId|transferId>                取消传输任务
retransmit <taskId|transferId>            请求断点重传
retransmit-accept <transferId>            接受重传请求
retransmit-reject <transferId>            拒绝重传请求
tasks                                     查看传输任务列表
task <taskId|transferId> [--once]         动态查看任务进度
open-received <taskId|transferId|fileName> 打开接收文件所在位置；按文件名查找时请使用 "" 或 '' 包起来
contacts                                  查看联系人
contact-add <accountId> [alias]           添加或更新联系人
contact-remove <contact-N|N>              删除联系人
contact-show <contact-N|N>                查看联系人详情
blacklist                                 查看黑名单
blacklist-add <accountId> [reason]        添加黑名单
blacklist-add-contact <contact-N|N>       将联系人加入黑名单
blacklist-remove <accountId>              删除黑名单
search-user <accountId>                   搜索在线用户
search-user-add <accountId> [alias]       搜索在线用户并加入联系人
public-key                                查看本地公钥
public-key-fingerprint [publicKey]        计算公钥指纹
account-id [publicKey]                    public-key-fingerprint 的别名
key-info                                  查看加密服务密钥状态
generate-key                              生成密钥对
delete-key                                删除密钥对
import-private-key <keyText>              导入私钥文本
import-private-key-file <path>            从文件导入私钥
import-private-key-paste                  粘贴多行私钥
exit                                      退出客户端
```

### help 语言切换

控制台默认使用英文显示 `help` 说明。输入 `language` 后，可以选择 `help` 说明界面的语言：

```text
language
1. English
2. Chinese
```

选择 `English` / `1` 后，后续 `help` 使用英文说明；选择 `Chinese` / `2` 后，后续 `help` 使用中文说明。该设置只影响 `help` 命令的说明界面，不会改变其他控制台命令的输出语言。

## 客户端 HTTP API

默认 base URL：

```text
http://127.0.0.1:20201
```

系统和密钥：

```text
GET  /api/system/status
GET  /api/system/key
POST /api/system/key/generate
POST /api/system/key/delete
POST /api/system/key/import-private
POST /api/system/key/fingerprint
POST /api/system/connect
POST /api/system/disconnect
GET  /api/system/connection-status
GET  /api/system/public-key
GET  /api/system/help
```

发送和任务：

```text
POST /api/send
GET  /api/send/tasks
GET  /api/send/tasks/{taskIdOrTransferId}
POST /api/send/tasks/{taskIdOrTransferId}/cancel
GET  /api/send/tasks/{taskIdOrTransferId}/events
```

接收：

```text
GET  /incoming
POST /accept
POST /reject
POST /retransmit
```

示例：

```bash
curl http://127.0.0.1:20201/api/system/status

curl -X POST http://127.0.0.1:20201/api/send \
  -H 'Content-Type: application/json' \
  -d '{"filePath":"./example.zip","targetAccountId":"<accountId>"}'
```

## 本地数据

客户端会在用户目录下保存本地状态：

```text
~/.file-security-transmission/device-id
~/.file-security-transmission/transfer-history.json
~/.file-security-transmission/local-data.db
```

其中：

- `device-id` 保存本机设备 ID。
- `transfer-history.json` 保存传输任务历史。
- `local-data.db` 保存联系人和黑名单等本地数据。

## 接收文件定位

接收到的文件默认保存到 `downloads-client-1`。用户可以在控制台中使用：

```text
open-received <taskId|transferId|"fileName">
```

该命令会在系统文件管理器中定位接收文件：

- macOS: Finder
- Windows: File Explorer
- Linux: 默认文件管理器

当参数是文件名时，请使用双引号或单引号包起来，例如：

```text
open-received "report.zip"
open-received 'report.zip'
```

## 启动方式

2026-05-06启动方式

目录结构

程序根目录/
├── scripts/
│   └── start-client.sh(macOS, Linux)

│   └── start-client.bat(Windows)

├── target/
│   └── FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar
