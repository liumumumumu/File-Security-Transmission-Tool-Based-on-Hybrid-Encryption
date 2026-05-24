# UI Vue 开发文档

## 1. 当前方案

当前 UI 是标准 Vite + Vue 工程：

```text
UI_Module/
  package.json
  vite.config.js
  index.html
  scripts/
    sync-static.mjs
  src/
    main.js
    App.vue
    styles.css
```

`index.html` 只是 Vite 入口，真正界面在 `src/App.vue`。样式集中在 `src/styles.css`。

## 2. 页面结构

当前主界面改成三个页面：

```text
发送
接收
联系人
```

`发送` 页包含：

- 接收方输入框与联系人下拉。
- 拖拽 / 选择文件区域。
- 当前发送进度与最近发送任务。

`接收` 页包含：

- 当前接收进度。
- 待接收请求列表。
- 高级接收操作。
- 补传请求区。

`联系人` 页包含：

- 联系人主列表。
- 联系人弹层（添加 / 修改）。
- 屏蔽名单子视图。

设置抽屉包含：

- 状态检测。
- 语言切换。
- 密钥管理。

## 3. 运行方式

第一次安装依赖：

```bash
cd UI_Module
npm install
```

开发预览：

```bash
npm run dev
```

生产构建：

```bash
npm run build
```

构建并同步到 Java 静态目录：

```bash
npm run build:static
```

## 4. Vite 代理

开发时页面运行在：

```text
http://127.0.0.1:5173/
```

Java 后端运行在：

```text
http://127.0.0.1:20201/
```

`vite.config.js` 已代理 `/api` 等路径到 Java 后端，所以 `App.vue` 里使用相对路径：

```js
fetch("/api/system/status")
```

打包进 Java 后，页面和 API 同源，也继续使用相对路径。

## 5. App.vue 主要状态

`activePage` 控制当前页面：

```js
activePage: "send"
```

核心列表状态：

```js
tasks: []
incomingRequests: []
retransmitRequests: []
contacts: []
blacklist: []
```

核心表单状态：

```js
sendForm: {
  filePath: "",
  targetAccountId: ""
}

keyForm: {
  privateKey: "",
  privateKeyPath: ""
}

receiveForm: {
  transferId: ""
}

sendRecipientInput: ""

contactsSubView: "list"

showSettingsDrawer: false

showContactModal: false

contactDraft: {
  contactIndex: null,
  accountId: "",
  alias: "",
  publicKey: "",
  reason: "",
}
```

## 6. 关键方法

统一接口调用：

```js
requestJson(path, options)
```

页面加载：

```js
loadDashboard()
loadTasks()
loadIncoming()
loadRetransmitRequests()
loadContacts()
loadBlacklist()
```

传输操作：

```js
generateKey()
deleteKey()
importPrivateKeyText()
importPrivateKeyFile()
pickSendFile()
pickPrivateKeyFile()
handleDragEnter()
handleDragOver()
handleDragLeave(event)
handleFileDrop(event)
handleFilePathInput()
sendFile()
acceptIncoming(transferId)
rejectIncoming(transferId)
manualAcceptReceive()
manualRejectReceive()
manualRequestRetransmit()
manualOpenReceived()
cancelSendTask(task)
requestRetransmit(task)
openReceived(task)
acceptRetransmit(transferId)
rejectRetransmit(transferId)
fillTargetFromContact(contact)
```

联系人操作：

```js
addContact()
searchUser()
searchUserAndAdd()
removeContact(contactIndex)
addBlacklist()
blacklistContact(contact)
removeBlacklist(accountId)
openContactCreateModal()
openContactEditModal(contact)
openBlacklistCreateModal()
submitContactModal()
```

所有操作完成后会自动刷新相关列表。

此外新增轻量辅助模块：

```js
src/ui-state.js
```

当前包含：

```js
hasLocalKeyPair()
defaultKeyPanelOpen()
extractFileNameFromPath()
extractLocalFileSelection()
toFriendlyErrorMessage()
```

对应测试文件：

```js
src/ui-state.test.js
```

## 7. 后端接口语义

按钮命名必须保持下面语义：

```text
发送方：取消发送
接收方：拒绝接收
接收方：请求补传
发送方：允许补传 / 拒绝补传
```

不要把“接收方拒绝接收”和“发送方取消发送”混成一个“取消”。

## 8. 开发注意

- 本轮 UI 主要改 `UI_Module`，并继续复用 `Desktop_Module` 中的原生文件选择桥，不改 Java 后端接口。
- 浏览器普通文件选择通常拿不到真实绝对路径，所以发送文件保留“本地文件路径”输入框。
- 文件拖拽的正式支持范围是 Electron 桌面壳；浏览器模式即使能收到 drop 事件，也只把它当作提示入口，不改为真正上传文件。
- Electron 桌面壳通过 `preload + contextBridge + ipc` 暴露原生文件选择器，返回真实绝对路径给前端表单。
- 发送目标在界面中叫“接收方”。可以手动输入 accountId 或 `contact-N`，也可以在发送页搜索联系人后自动填入 accountId。
- 密钥管理入口只放在设置抽屉中，不再放在主页。
- 生成密钥若命中“已有密钥”冲突，应显示业务提示，而不是泛化成服务不可用。
- 顶部状态条被移除，状态检测放进设置抽屉。
- 接收方的“拒绝接收”和发送方的“取消发送”是不同语义，不要合并按钮文案。
- `transferId/taskId` 这类字段只放在高级操作说明中，默认界面不要直接暴露技术字段名。
- 如果 UI 显示 HTTP 500，优先检查 Java 后端和 Python crypto service 是否都已启动。
- 如果 Vue dev 页面返回 HTML 而不是 JSON，通常是 Java 后端没启动或 Vite proxy 目标不通。
- 如果 WSL Electron 中文显示方框，需要安装中文字体，如 `fonts-noto-cjk`。

## 9. WSL/Linux 本地后端

本轮不修改 Java 后端源码，也不做 Linux 安装包。所谓 Linux 后端是指在 WSL/Linux 本地运行已有 `TCP_Module` Spring Boot 客户端后端。

手动启动顺序：

```bash
cd Encryption_module
source .venv/bin/activate
python crypto_service/main.py --host 127.0.0.1 --port 20202 --key-dir crypto_keys
```

```bash
cd TCP_Module
CLIENT_HTTP_PORT=20201 \
CRYPTO_SERVICE_PORT=20202 \
NODE_AUTO_CONNECT=false \
./scripts/start-client.sh
```

项目外快捷脚本：

```bash
/home/mfxian/FileSecurityTransmission_Local/run-linux-stack.sh
```

脚本会把虚拟环境、密钥和日志放在 `/home/mfxian/FileSecurityTransmission_Local/`，避免污染项目目录。

验证：

```bash
curl http://127.0.0.1:20202/key/status
curl http://127.0.0.1:20201/api/system/status
curl http://127.0.0.1:20201/api/send/tasks
curl http://127.0.0.1:20201/api/contacts
```
