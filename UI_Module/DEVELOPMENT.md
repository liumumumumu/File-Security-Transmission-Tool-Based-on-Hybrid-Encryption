# UI Vue 开发文档

## 1. 当前方案

当前界面使用 Vue 3 CDN 静态版：

- 优点：不需要 Node.js，不需要构建工具，双击 `index.html` 就能预览。
- 缺点：Vue 和 Lucide 图标来自 CDN，浏览器需要能访问互联网；代码暂时没有 `.vue` 单文件组件。
- 后续升级：安装 Node.js 后，可以迁移到 Vite + Vue，拆成组件和模块。

当前已把 `index.html`、`app.js`、`styles.css` 同步到 `TCP_Module/src/main/resources/static/`。启动 Java 后端后访问 `http://127.0.0.1:20201/`，浏览器页面和 REST API 同源，能避开双击 `file://` 打开时的 CORS 问题。

新版 UI 的设计目标是“更像应用，而不是网页宣传页”：

- 固定左侧导航：对应真实 App 常见的信息架构。
- 顶部状态栏：显示当前工作区和连接状态。
- 中央工作区：把发送/接收、进度、任务队列、安全流程放进仪表盘。
- 浅色高级配色：白色面板、冷灰背景、墨绿主色、深海蓝/琥珀/柔红辅助色。

当前文件分工：

| 文件 | 你主要看什么 |
| --- | --- |
| `index.html` | Vue 模板、指令、表单结构 |
| `app.js` | Vue 状态、计算属性、事件方法、后端调用 |
| `styles.css` | CSS 布局、颜色、响应式 |

## 2. Vue 代码怎么读

### data

`data()` 里放页面状态。例如：

```js
data() {
  return {
    mode: "send",
    files: [],
    progress: 0,
    status: "idle"
  };
}
```

你可以把它理解成“页面记忆”。用户选了文件、点击按钮、进度变化，都会改这里的数据。

### computed

`computed` 是从已有状态计算出来的展示值。例如：

```js
totalBytes() {
  return this.files.reduce((sum, file) => sum + file.size, 0);
}
```

它不直接保存新数据，而是根据 `files` 自动算总大小。`files` 一变，`totalBytes` 会自动变。

### methods

`methods` 放用户操作和业务逻辑。例如：

```js
startTransfer() {
  this.status = "connecting";
}
```

按钮点击、文件选择、拖拽上传、调用后端接口都写在这里。

## 3. 常用 Vue 指令

| 写法 | 作用 | 本项目例子 |
| --- | --- | --- |
| `{{ text }}` | 显示变量 | `{{ progress }}%` |
| `v-if` | 条件渲染 | 没有文件时显示空状态 |
| `v-for` | 列表渲染 | 遍历文件列表和安全流程 |
| `v-model` | 表单双向绑定 | 服务器地址、端口、分块大小 |
| `:class` | 动态 class | 根据状态切换在线/失败样式 |
| `:style` | 动态样式 | 进度条宽度 |
| `@click` | 点击事件 | 暂停、恢复、清空 |
| `@submit.prevent` | 阻止表单刷新并执行方法 | 开始传输 |

新版还使用了 `data-lucide` 图标：

```html
<i data-lucide="send"></i>
```

`app.js` 中的 `refreshIcons()` 会在 Vue 渲染后调用 Lucide，把这些占位标签替换成 SVG 图标。

## 4. 当前状态流

UI 使用这些状态表示传输过程：

| 状态 | 含义 | UI 展示 |
| --- | --- | --- |
| `idle` | 初始状态 | 未连接、等待任务 |
| `connecting` | 正在连接后端/TCP 服务 | 连接中 |
| `key_exchange` | 正在协商会话密钥 | 协商密钥 |
| `encrypting_chunks` | 正在分块加密和发送 | 传输中 |
| `waiting_ack` | 等待接收端 ACK | 等待 ACK |
| `paused` | 暂停 | 已暂停 |
| `resuming` | 从断点恢复 | 恢复中 |
| `completed` | 完成 | 已完成 |
| `failed` | 失败 | 失败 |

这些状态后续应该和后端返回的状态保持一致。

## 5. 后端接口契约建议

根据目前分工，前端只对接 LQH 的 Java Spring Boot 后端：

```text
UI_Module(Vue)
  -> TCP_Module(Java Spring Boot REST API, HTTP 20201)
  -> TCP_Module(Netty TCP, TCP 9000)
  -> Crypto Service(Qt/C++ + OpenSSL, HTTP 20202)
```

Crypto service 负责 RSA、签名、AES 密钥包裹、AES-256-GCM 数据块加解密。`TCP_Module` 负责调用这些能力，Vue 不直接调用加密函数。

LQH 分支目前已有：

```http
GET  /api/system/status
GET  /api/system/key
POST /api/system/key/generate
POST /api/system/key/delete
POST /api/system/key/import-private
```

这些接口已经接入 UI 的“系统状态 / 密钥状态”面板。页面默认 Java API 为：

```text
http://127.0.0.1:20201
```

当前 Java 传输接口建议直接按后端实现来对齐，而不是继续沿用旧的 `/api/transfers` 设计。

### 创建传输任务

```http
POST /api/send
Content-Type: application/json
```

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `filePath` | 字符串 | 发送方本机文件路径 |
| `targetAccountId` | 字符串 | 接收方账号指纹 |

`request` 示例：

```json
{
  "filePath": "C:\\Users\\15328\\Desktop\\demo.zip",
  "targetAccountId": "eae3cb046c283e9426e074a743efcd9a2abcb3e7e3b5cb50a0b5084c3ee8e911"
}
```

响应示例：

```json
{
  "success": true,
  "taskId": "task_001",
  "targetAccountId": "eae3cb046c283e9426e074a743efcd9a2abcb3e7e3b5cb50a0b5084c3ee8e911",
  "message": "Send task created"
}
```

### 查询任务状态

```http
GET /api/send/tasks/{task_id}
```

响应示例：

```json
{
  "taskId": "task_001",
  "status": "TRANSFERRING",
  "progress": 62,
  "speedMegabytesPerSecond": 11.8,
  "speedText": "11.80 mb/s",
  "transferredBlocks": 620,
  "totalBlocks": 1000
}
```

### CORS 注意

如果页面通过双击打开，浏览器地址是 `file://.../index.html`。Spring Boot 后端需要允许来自本地页面的跨域请求。开发阶段可以先允许所有来源，答辩前再收紧。

## 6. 后端方法如何体现在前端

前端不能直接调用 Java 方法或加密函数，必须通过 HTTP 接口间接调用。

错误理解：

```js
// 浏览器里不能直接这样调用 Java/Qt/C++ 函数
start_transfer(filePath, targetAccountId);
```

正确结构：

```text
Vue 页面 -> fetch/JSON -> Java Spring Boot 接口 -> Java 调用 TCP/加密模块
```

本项目中的 `createBackendTransfer()` 就是这个入口。当前 Vue 版已经改成对接 `POST /api/send` 和 `GET /api/send/tasks/{task_id}`；如果只保留浏览器版，就还需要给用户一个可输入的本地文件路径。
浏览器文件选择器通常拿不到稳定的绝对路径，所以前端只能帮你预填文件名或相对路径，真实联调时最好手动改成 Java 后端能访问的路径。

## 7. 你需要补的知识

### 第一阶段：能改界面

- HTML：`form`、`input type="file"`、`button`、`section`。
- CSS：盒模型、颜色变量、Grid、Flex、媒体查询、`position: sticky`。
- Vue 模板：`v-if`、`v-for`、`v-model`、事件绑定。
- 图标库：理解 `data-lucide` 如何变成按钮里的图标。

### 第二阶段：能看懂交互

- JavaScript 变量、数组、对象、函数。
- `File` 对象：`file.name`、`file.size`、`file.lastModified`。
- 定时器：`setInterval`、`clearInterval`。
- Promise 和 `async/await`。

### 第三阶段：能接后端

- `fetch()` 请求。
- `FormData` 上传文件。
- JSON 解析。
- HTTP 状态码。
- CORS。
- 轮询和 WebSocket 的区别。

### 第四阶段：项目相关理解

- RSA/ECC 只负责密钥协商，不直接加密大文件。
- 签名/验签用于身份认证，例如 challenge 由私钥签名、公钥验证。
- AES 密钥包裹是指用接收方 RSA 公钥加密 AES 会话密钥，接收方再用私钥解开。
- AES-256-GCM 负责文件块加密和完整性校验。
- 分块传输需要块序号、块长度、nonce、tag、ciphertext。
- ACK 用来确认接收端已经成功收到并校验某个块。
- 断点续传依赖“最后确认块”或“已确认块集合”。

## 8. 推荐学习顺序

1. 先打开页面，点一遍发送、暂停、恢复、清空。
2. 看 `index.html`，先理解 `sidebar / topbar / workspace / panel` 这些区域。
3. 找按钮上的 `@click` 或 `@submit.prevent`，再到 `app.js` 里看对应方法。
4. 改一个字段，比如默认端口或默认分块大小，刷新页面看变化。
5. 改一处样式，比如 `--primary`、`--blue`、`--gold`，观察整体配色变化。
6. 和后端同学约定接口字段，然后关闭演示模式联调。

## 9. 后续迁移到 Vite 的方向

等你安装 Node.js 后，可以把当前结构升级成：

```text
UI_Module/
  package.json
  index.html
  src/
    main.js
    App.vue
    components/
      TransferPanel.vue
      StatusPanel.vue
      PipelinePanel.vue
    api/
      transfers.js
```

当前这版 Vue CDN 模板就是为了让你先理解 Vue 思维，之后迁移时不会突然面对一堆工具链。
