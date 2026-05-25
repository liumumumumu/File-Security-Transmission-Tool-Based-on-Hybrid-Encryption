# UI_Module

本目录是“基于混合加密的文件安全传输工具”的 Vue 前端界面模块。当前采用 Vue 3 CDN 静态版，不需要安装 Node.js，也不需要启动开发服务器。新版界面采用浅色 App Shell：左侧导航、顶部状态栏、传输工作台、任务队列、安全链路、系统状态和后端联调区。

## 当前文件

| 文件 | 作用 |
| --- | --- |
| `index.html` | Vue 模板：App Shell、侧边栏、传输工作台、任务队列、安全链路、系统/密钥状态 |
| `app.js` | Vue 应用逻辑：导航状态、文件状态、演示传输、后端接口调用 |
| `styles.css` | 页面样式：浅色高级配色、响应式布局、仪表盘组件 |
| `DEVELOPMENT.md` | 开发文档：Vue 知识点、接口设计、学习路线 |
| `TESTING.md` | Java / Qt crypto service / UI 联调测试记录 |

## 如何预览

正式联调时推荐先启动 Java 后端，然后直接打开：

```text
http://127.0.0.1:20201/
```

这个地址会读取 `TCP_Module/src/main/resources/static/` 中的内置 UI，和后端接口同源，不会出现 `file://` 跨域问题。

在 WSL 里进入本目录：

```bash
cd /home/mfxian/File-Security-Transmission-Tool-Based-on-Hybrid-Encryption/UI_Module
explorer.exe .
```

Windows 文件资源管理器打开后，双击 `index.html` 即可。

也可以在 Windows 文件资源管理器地址栏输入：

```text
\\wsl$\Ubuntu\home\mfxian\File-Security-Transmission-Tool-Based-on-Hybrid-Encryption\UI_Module
```

如果你的 WSL 发行版不叫 `Ubuntu`，用 PowerShell 查看：

```powershell
wsl -l -v
```

然后把路径中的 `Ubuntu` 换成实际名称。

## 联调测试记录

本次 Java21、Spring Boot、Qt crypto service、9080/9081 端口冲突、`start-client.bat` 参数修改等排查过程，整理在：

```text
UI_Module/TESTING.md
```

## Vue 使用方式

当前页面通过 CDN 引入 Vue：

```html
<script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
<script src="https://unpkg.com/lucide@latest"></script>
<script src="./app.js"></script>
```

所以浏览器需要能访问互联网来加载 Vue 和 Lucide 图标。后续如果改成 Vite/Vue 工程，就可以把这些依赖安装到本地。

## 界面设计说明

- 采用更像桌面应用的布局：左侧功能导航 + 顶部状态栏 + 中央工作区。
- 浅色风格使用低饱和背景、白色面板、细边框和柔和阴影。
- 配色不是单一蓝紫，而是墨绿、深海蓝、琥珀、柔和红分层表达状态。
- 主操作区突出“选择文件 / 开始传输”，右侧使用圆形进度仪表盘强化 App 感。
- 下方保留任务队列、安全链路、系统状态、密钥状态和后端联调信息，方便答辩说明架构。

## 当前功能

- 发送/接收模式切换。
- 文件选择和拖拽上传。
- 自动计算文件总大小和分块数量。
- 配置服务器地址、端口、密钥协商算法、分块大小。
- 演示模式下模拟连接、密钥协商、分块加密、ACK、完成状态。
- 暂停、恢复、清空任务。
- 调用 Java 后端同步系统状态和密钥状态。
- 调用 Java 后端生成、删除本地 RSA 密钥对。
- 接口配置面板，预留真实后端 API 对接。
- 任务日志，帮助观察 Vue 状态变化。
- 侧边栏导航和 App 工作台式布局。

## 后端对接思路

当前团队分工按下面理解：

```text
UI_Module(Vue) -> TCP_Module(Java Spring Boot / LQH) -> Crypto Service(Qt/C++ / OpenSSL)
```

Vue 不直接调用加密函数，也不直接处理 TCP socket。LQH 的 Java 后端负责把前端请求转成 TCP 传输流程，并在内部调用 Qt crypto service。

当前 UI 已经对接这些 Java 接口：

```http
GET  http://127.0.0.1:20201/api/system/status
GET  http://127.0.0.1:20201/api/system/key
POST http://127.0.0.1:20201/api/system/key/generate
POST http://127.0.0.1:20201/api/system/key/delete
```

关闭“演示模式”后，前端会尝试调用：

```http
POST http://127.0.0.1:20201/api/send
GET  http://127.0.0.1:20201/api/send/tasks/{task_id}
```

后端需要注意：

- `POST /api/send` 接收 JSON：`filePath` 是发送方本机路径，`targetAccountId` 是接收方账号指纹。
- 浏览器选文件后通常只能拿到文件名或相对路径，真实联调时建议把 `filePath` 手动改成 Java 后端能访问的绝对路径。
- 返回 JSON，至少包含 `taskId` 和 `success`。
- 状态查询接口返回 `status`、`progress`、`transferredBlocks`、`speedMegabytesPerSecond`。
- 当前仓库默认 Spring Boot HTTP 端口为 `20201`，TCP 传输服务端口为 `9000`，Qt crypto service 默认端口为 `20202`。
- 如果前端从 `file://` 打开页面，Java 后端需要允许 CORS。

## 你现在优先学习

1. Vue 模板语法：`{{ }}`、`v-if`、`v-for`、`:class`、`:style`。
2. Vue 表单绑定：`v-model`、`v-model.number`、复选框绑定。
3. Vue 事件：`@click`、`@submit.prevent`、`@change`、拖拽事件。
4. Vue 状态：`data()` 保存状态，`computed` 计算展示值，`methods` 放交互逻辑。
5. CSS 基础：盒模型、Grid、Flex、响应式媒体查询。
6. 后端接口：`fetch()`、`FormData`、JSON、轮询任务状态。

## 参考界面

- [SendFiles.online](https://sendfiles.online/)
- [Wormhole](https://wormhole.app/)
- [FILE.CM](https://file.cm/)
- [Send Anywhere](https://send-anywhere.com/)
- [Simple.Savr](https://www.ssavr.com/)
- [note.ms](https://note.ms/)
