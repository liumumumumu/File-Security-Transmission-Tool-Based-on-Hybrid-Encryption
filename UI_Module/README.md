# UI_Module

本目录是“基于混合加密的文件安全传输工具”的前端界面模块。当前先提供一个无依赖静态原型，帮助界面同学学习布局、交互和传输状态表达，后续再接入 Python 客户端/服务端的真实传输逻辑。

## 当前文件

| 文件 | 作用 |
| --- | --- |
| `index.html` | 页面结构：发送区、接收区、加密选项、进度状态、安全流程 |
| `styles.css` | 页面样式：响应式布局、上传区、按钮、进度条、状态面板 |
| `app.js` | 交互模拟：选择文件、拖拽文件、模拟进度、切换发送/接收 |
| `DEVELOPMENT.md` | 开发文档：学习路线、模块设计、后端接口约定、协作流程 |

## 如何预览

直接在浏览器中打开：

```text
UI_Module/index.html
```

当前页面不需要安装 Node.js 或任何前端框架。选择文件后点击“开始安全传输”，页面会模拟密钥协商、分块加密、ACK 和进度更新。

## UI 目标

界面参考了 SendFiles.online、Wormhole、FILE.CM/Send.now、Send Anywhere、Simple.Savr、note.ms 一类文件/文本分享产品的共同思路：

- 首屏直接提供文件选择或拖拽上传，减少用户进入任务的步骤。
- 把过期时间、密码、加密、下载限制等关键选项放在上传区附近。
- 使用短链接、接收码或房间号作为“发送端和接收端连接”的入口。
- 对本课题额外展示混合加密、分块、ACK、断点续传等课程评分点。

## 本项目中的界面职责

前端只负责展示和收集操作，不直接实现密码学算法：

- 选择文件、显示文件名、大小、分块数量。
- 配置服务器地址、端口、密钥协商算法、分块大小。
- 展示连接状态、传输百分比、速度、ETA、已确认块数量。
- 展示 RSA/ECC 协商 AES 会话密钥、AES-256-GCM 分块加密、GCM Tag、ACK、断点续传的流程。
- 调用后端接口开始、暂停、恢复、取消任务。

## 后续开发路线

1. 保持当前静态页面可运行，先完成界面布局和交互学习。
2. 让后端提供本地 API，例如 `http://127.0.0.1:1749/api/transfers`。
3. 前端把模拟进度替换成真实接口返回的数据。
4. 增加错误状态：连接失败、密钥协商失败、Tag 校验失败、断点续传失败。
5. 项目答辩前补齐演示脚本、截图和功能说明。

## 参考来源

- [SendFiles.online](https://sendfiles.online/)
- [Wormhole](https://wormhole.app/)
- [Wormhole Security Design](https://wormhole.app/security)
- [FILE.CM / Send.now](https://file.cm/)
- [Send Anywhere Help Center](https://support.send-anywhere.com/hc/en-us/articles/115003736493-How-can-I-send-files)
- [Simple.Savr](https://www.ssavr.com/)
- [note.ms/niay](https://note.ms/niay)
