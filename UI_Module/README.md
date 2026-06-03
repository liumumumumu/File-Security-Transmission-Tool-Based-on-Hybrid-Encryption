# UI_Module

本目录是“基于混合加密的文件安全传输工具”的 Vue 前端界面模块。当前采用 Vite + Vue 单文件组件，界面代码位于 `src/App.vue`，样式位于 `src/styles.css`。

## 当前界面

UI 已从展示型仪表盘改为更像聊天软件的轻量应用：

- `发送`：选择联系人、拖拽或选择文件、查看发送进度。
- `接收`：查看待接收文件、处理高级操作、查看接收与补传进度。
- `联系人`：主列表视图与屏蔽名单子视图。
- `设置抽屉`：状态检测、语言切换、密钥管理。

前端只调用 Java Spring Boot HTTP 接口，不直接处理 TCP socket，也不直接调用加密函数。

```text
UI_Module(Vue)
  -> TCP_Module(Java REST, HTTP 20201)
  -> TCP_Module(Netty TCP, TCP 9000)
  -> Encryption_module(Python crypto service, HTTP 20202)
```

## 文件结构

| 文件 | 作用 |
| --- | --- |
| `package.json` | Vite/Vue 项目依赖和脚本 |
| `vite.config.js` | Vite 开发服务器、构建配置和后端代理 |
| `index.html` | Vite HTML 入口，只挂载 `src/main.js` |
| `src/main.js` | 创建 Vue 应用并挂载 `App.vue` |
| `src/App.vue` | 三个主视图 + 设置抽屉 + 联系人弹层 + 后端调用 |
| `src/styles.css` | 极简桌面 App 布局、列表视图、设置抽屉与弹层样式 |
| `src/ui-state.js` | 轻量前端状态和文件路径辅助函数 |
| `src/ui-state.test.js` | `node:test` 轻量测试，覆盖密钥状态与拖拽路径提取 |
| `scripts/sync-static.mjs` | 将 `dist/` 同步到 Java 静态资源目录 |
| `DEVELOPMENT.md` | 开发说明和接口契约 |
| `TESTING.md` | 联调测试记录 |

## 预览

安装依赖：

```bash
cd UI_Module
npm install
```

开发预览：

```bash
npm run dev
```

访问：

```text
http://127.0.0.1:5173/
```

`vite.config.js` 已把 `/api`、`/incoming`、`/accept`、`/reject`、`/retransmit` 等请求代理到：

```text
http://127.0.0.1:20201
```

开发时不要在 UI 中填写 `5173` 作为 Java API；留空即可使用 Vite proxy。

## 联调启动顺序

建议按下面顺序启动：

```text
1. 启动 Encryption_module Python 加密服务，端口 20202
2. 启动 TCP_Module Java 客户端后端，端口 20201
3. 启动 UI_Module Vite 前端，端口 5173
4. 可选：启动 Desktop_Module Electron 壳
```

Python 加密服务：

```bash
cd Encryption_module
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python crypto_service/main.py --host 127.0.0.1 --port 20202 --key-dir crypto_keys
```

Java 后端：

```bash
cd TCP_Module
CLIENT_HTTP_PORT=20201 \
CRYPTO_SERVICE_PORT=20202 \
NODE_AUTO_CONNECT=false \
./scripts/start-client.sh
```

如果希望在 WSL/Linux 本地一键拉起 Python 加密服务和 Java 后端，可以使用项目外脚本：

```bash
/home/mfxian/FileSecurityTransmission_Local/run-linux-stack.sh
```

该脚本放在项目外，不会修改 `TCP_Module` 或 `Encryption_module` 的跟踪文件。它会优先使用已有 jar；如果 jar 不存在，只会在 ignored 的 `TCP_Module/target/` 下执行 Maven 打包。

验证接口：

```bash
curl http://127.0.0.1:20202/key/status
curl http://127.0.0.1:20201/api/system/status
curl http://127.0.0.1:20201/api/send/tasks
curl http://127.0.0.1:20201/api/contacts
```

## 构建

构建生产版本：

```bash
npm run build
```

构建并同步给 Java 静态目录：

```bash
npm run build:static
```

同步后 Java 会从下面目录托管 UI：

```text
TCP_Module/src/main/resources/static/
```

访问：

```text
http://127.0.0.1:20201/
```

## 已接入接口

系统：

```http
GET  /api/system/status
GET  /api/system/key
GET  /api/system/connection-status
POST /api/system/key/generate
POST /api/system/key/import-private
POST /api/system/key/import-private-file
```

发送与任务：

```http
POST /api/send
GET  /api/send/tasks
POST /api/send/tasks/{taskIdOrTransferId}/cancel
```

接收与补传：

```http
GET  /api/receive/incoming
POST /api/receive/accept
POST /api/receive/reject
POST /api/receive/retransmit
GET  /api/receive/retransmit-requests
POST /api/receive/retransmit-accept
POST /api/receive/retransmit-reject
POST /api/receive/open-received
```

联系人与屏蔽名单：

```http
GET    /api/contacts
POST   /api/contacts
DELETE /api/contacts/{contactIndex}
GET    /api/contacts/search-user/{accountId}
POST   /api/contacts/search-user-add
GET    /api/contacts/blacklist
POST   /api/contacts/blacklist
POST   /api/contacts/blacklist/contact/{contactIndex}
DELETE /api/contacts/blacklist/{accountId}
```

## 设计约定

- 顶部不再显示本机 / 服务器 / 密钥状态条，状态检测统一放进设置抽屉。
- 密钥管理不再放在主页，统一收进设置抽屉。
- 发送与接收拆成独立页面，不再同屏混排。
- 联系人与屏蔽名单不再用大表单同屏堆叠，而是使用中间主列表 + 弹层 / 子视图。
- “取消发送”只用于发送方任务取消。
- “拒绝接收”只用于接收方 incoming 请求。
- “请求补传”由接收方发起，底层仍调用重传接口。
- “允许补传 / 拒绝”由发送方处理，底层仍调用重传接口。
- 当前没有独立白名单接口，联系人承担可信对象/常用对象角色。
- 发送页支持搜索联系人备注名、`contact-N` 或 accountId，点击联系人会自动填入目标 accountId。
- Electron 桌面壳支持拖拽文件和点击弹系统文件选择器；浏览器模式仍以手动填写 `filePath` / `privateKeyPath` 为准。
- 如果本机已有密钥，再次点击“生成密钥”会显示准确业务提示，而不是误报服务不可用。
