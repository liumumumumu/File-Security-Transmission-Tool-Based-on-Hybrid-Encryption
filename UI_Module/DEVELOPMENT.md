# UI 开发文档

## 1. 总体框架分析

本项目的核心不是普通网盘，而是“文件传输 + 混合加密 + 可靠 TCP 协议”的可视化客户端。因此 UI 需要同时服务两个目标：

- 对用户：快速选择文件，看到传输是否成功、速度如何、还要多久。
- 对课程验收：清楚展示 RSA/ECC 密钥协商、AES-256-GCM 分块加密、GCM Tag、ACK、断点续传等关键点。

建议前端先采用三层结构：

| 层级 | 作用 | 当前模板对应位置 |
| --- | --- | --- |
| 表现层 | HTML/CSS 页面、响应式布局、按钮、表单、进度条 | `index.html`, `styles.css` |
| 交互层 | 文件选择、拖拽、状态切换、调用 API、更新 DOM | `app.js` |
| 后端适配层 | 把 UI 操作转成后端命令，把后端状态转成 UI 状态 | 后续从 `app.js` 中拆出 `api.js` |

当前模板故意不用 Vue/React，是为了降低第一阶段学习成本。等你熟悉 HTML/CSS/JS 和真实接口后，如果老师或团队需要更规范的组件化，再迁移到 Vue、React 或 PySide6。

## 2. 推荐界面结构

第一屏保持“能立刻发送文件”的结构：

- 顶部导航：项目名、连接状态、开发模式入口。
- 左侧说明：一句话告诉用户这是安全文件传输工具。
- 右侧主操作区：发送/接收切换、拖拽上传、服务器地址、端口、加密选项。
- 下方状态区：进度条、速度、ETA、已确认块、会话密钥状态。
- 安全流程区：连接服务器、协商密钥、分块加密、ACK/断点续传。

这样既借鉴了文件分享网站“上传入口优先”的体验，也符合本课题需要展示底层技术流程的要求。

## 3. 参考网站的可借鉴点

| 网站 | 可以借鉴 | 本项目采用方式 |
| --- | --- | --- |
| SendFiles.online | 上传入口直接、流程轻量 | 首屏放大拖拽上传区 |
| Wormhole | 强调安全分享和临时链接 | 展示会话密钥、加密流程、接收码 |
| FILE.CM / Send.now | 上传后生成分享信息 | 后续可生成 task_id 或 receive_code |
| Send Anywhere | 使用 6 位码/链接连接收发双方 | 接收区保留“接收码”输入 |
| Simple.Savr | 局域网内快速共享的轻量感 | 支持服务器地址和端口配置 |
| note.ms | 极简房间/短路径协作 | 只借鉴短路径/会话码思路 |

注意：这些站点主要是产品体验参考，不能直接复制样式、文案或业务规则。本项目应突出“安全传输工具”的课程特色。

## 4. 前端需要的知识储备

### HTML

- 常用标签：`header`, `main`, `section`, `form`, `label`, `input`, `select`, `button`。
- 文件输入：`<input type="file" multiple>`。
- 可访问性基础：`aria-label`, `aria-live`, 表单标签和按钮语义。

### CSS

- 盒模型：`box-sizing`, `padding`, `border`, `margin`。
- 布局：Flexbox、CSS Grid、`minmax()`, `clamp()`, 媒体查询。
- 视觉系统：颜色变量、间距、边框、阴影、按钮状态。
- 响应式：手机端单列布局，桌面端双列布局。

### JavaScript

- DOM 查询和事件监听：`querySelector`, `addEventListener`。
- 文件对象：`File`, `FileList`, `file.name`, `file.size`。
- 拖拽事件：`dragenter`, `dragover`, `dragleave`, `drop`。
- 状态更新：用变量保存文件列表、进度、定时器。
- 后续接口请求：`fetch()` 或 WebSocket。

### 项目相关知识

- TCP 客户端/服务端基本流程：连接、发送、接收、关闭。
- 混合加密：非对称算法保护会话密钥，对称算法加密大文件数据。
- AES-256-GCM：密文 + Tag，能同时提供保密性和完整性校验。
- 文件分块：大文件按固定大小拆分，每块包含序号、长度、密文、Tag。
- ACK：接收端确认块序号，发送端据此更新进度。
- 断点续传：保存已确认块，重连后从缺失块继续。

## 5. 建议的前后端接口契约

真实后端尚未接入前，可以先约定接口形状。前端以后把 `app.js` 中的模拟进度替换成这些 API。

### 创建传输任务

```http
POST /api/transfers
Content-Type: application/json
```

请求示例：

```json
{
  "mode": "send",
  "server_host": "127.0.0.1",
  "server_port": 1749,
  "key_exchange": "RSA-2048",
  "cipher": "AES-256-GCM",
  "chunk_size_mb": 1,
  "files": [
    {
      "name": "demo.zip",
      "size": 10485760
    }
  ]
}
```

响应示例：

```json
{
  "task_id": "task_1749_001",
  "receive_code": "SH-1749-ABCD",
  "status": "created"
}
```

### 查询任务状态

```http
GET /api/transfers/{task_id}
```

响应示例：

```json
{
  "task_id": "task_1749_001",
  "status": "transferring",
  "progress": 62,
  "speed_mbps": 11.8,
  "eta_seconds": 14,
  "acked_chunks": 620,
  "total_chunks": 1000,
  "current_stage": "encrypting_chunks"
}
```

### 暂停、恢复、取消

```http
POST /api/transfers/{task_id}/pause
POST /api/transfers/{task_id}/resume
POST /api/transfers/{task_id}/cancel
```

## 6. UI 状态设计

| 状态 | 展示文案 | 触发条件 |
| --- | --- | --- |
| idle | 等待任务 | 初始页面或清空后 |
| connecting | 正在连接服务器 | 点击开始传输 |
| key_exchange | 正在协商密钥 | TCP 连接成功后 |
| encrypting_chunks | 正在加密并发送分块 | 后端开始读取文件 |
| waiting_ack | 等待接收端确认 | 已发送块但 ACK 未返回 |
| paused | 已暂停 | 用户暂停或连接断开 |
| resuming | 正在恢复传输 | 根据断点状态继续 |
| completed | 传输完成 | 所有块确认完成 |
| failed | 传输失败 | 网络、加密或校验失败 |

## 7. 当前模板怎么继续改

建议你按这个顺序学习和修改：

1. 先读 `index.html`，弄清每个区域对应页面上的哪一块。
2. 改 `styles.css` 中的颜色变量和间距，观察界面变化。
3. 在 `app.js` 中给按钮添加新状态，例如暂停、恢复、取消。
4. 把模拟进度抽成函数：`startTransfer()`, `pauseTransfer()`, `resumeTransfer()`。
5. 等后端同学提供接口后，把 `setInterval()` 模拟替换成 `fetch()` 或 WebSocket。

## 8. Git 协作建议

- 你的 UI 分支只改 `UI_Module/`，减少和密码模块、TCP 模块冲突。
- 每次提交前运行一次页面预览，确认选择文件、拖拽、进度模拟没有坏。
- 提交信息建议写清楚范围，例如 `ui: add transfer dashboard prototype`。
- 合并前在 README 中同步说明新增文件和运行方法。

## 9. 答辩展示建议

演示顺序可以这样安排：

1. 打开 UI，选择一个大文件。
2. 展示密钥协商算法选择和 AES-256-GCM 选项。
3. 点击开始传输，说明每块 1 MB、每块独立 Tag。
4. 展示进度、速度、ETA、ACK 数量。
5. 模拟断线后恢复，说明根据已确认块继续。
6. 最后展示性能测试结果和安全分析。
