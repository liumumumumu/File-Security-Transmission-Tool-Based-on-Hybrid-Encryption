import Foundation

enum AppLanguage: String, Codable, CaseIterable, Identifiable {
    case english = "ENGLISH"
    case chinese = "CHINESE"

    var id: String { rawValue }

    var apiValue: String {
        switch self {
        case .english:
            "english"
        case .chinese:
            "chinese"
        }
    }

    var displayName: String {
        switch self {
        case .english:
            "English"
        case .chinese:
            "中文"
        }
    }

    init(apiValue: String?) {
        switch apiValue?.lowercased() {
        case "chinese", "zh", "cn":
            self = .chinese
        default:
            self = .english
        }
    }
}

struct AppStrings {
    let language: AppLanguage

    func text(_ key: Key) -> String {
        switch (language, key) {
        case (_, .appTitle): "File Security Transmission"
        case (.english, .relayTab): "Relay Transfer"
        case (.chinese, .relayTab): "中继传输"
        case (.english, .offlineTab): "Offline Crypto"
        case (.chinese, .offlineTab): "离线加解密"
        case (.english, .localService): "Local Service"
        case (.chinese, .localService): "本地服务"
        case (.english, .starting): "Starting local service..."
        case (.chinese, .starting): "本地服务启动中..."
        case (.english, .unavailable): "Local service is unavailable"
        case (.chinese, .unavailable): "本地服务不可用"
        case (.english, .retry): "Retry"
        case (.chinese, .retry): "重试"
        case (.english, .openConsole): "Open Console"
        case (.chinese, .openConsole): "打开控制台"
        case (.english, .language): "Language"
        case (.chinese, .language): "语言"
        case (.english, .connected): "Connected"
        case (.chinese, .connected): "已连接"
        case (.english, .disconnected): "Disconnected"
        case (.chinese, .disconnected): "未连接"
        case (.english, .serviceReady): "Service Ready"
        case (.chinese, .serviceReady): "服务已就绪"
        case (.english, .accountId): "Account ID"
        case (.chinese, .accountId): "账号 ID"
        case (.english, .deviceId): "Device ID"
        case (.chinese, .deviceId): "设备 ID"
        case (.english, .server): "Server"
        case (.chinese, .server): "服务器"
        case (.english, .host): "Host"
        case (.chinese, .host): "主机"
        case (.english, .port): "Port"
        case (.chinese, .port): "端口"
        case (.english, .connect): "Connect"
        case (.chinese, .connect): "连接"
        case (.english, .disconnect): "Disconnect"
        case (.chinese, .disconnect): "断开"
        case (.english, .authenticated): "Authenticated"
        case (.chinese, .authenticated): "已认证"
        case (.english, .notAuthenticated): "Not Authenticated"
        case (.chinese, .notAuthenticated): "未认证"
        case (.english, .temporarySettings): "Temporary connection settings"
        case (.chinese, .temporarySettings): "本次临时连接配置"
        case (.english, .refresh): "Refresh"
        case (.chinese, .refresh): "刷新"
        case (.english, .invalidPort): "Enter a valid port."
        case (.chinese, .invalidPort): "请输入有效端口。"
        case (.english, .chooseFile): "Choose File"
        case (.chinese, .chooseFile): "选择文件"
        case (.english, .dropFile): "Drop a file here"
        case (.chinese, .dropFile): "将文件拖到这里"
        case (.english, .selectedFile): "Selected File"
        case (.chinese, .selectedFile): "已选文件"
        case (.english, .target): "Target"
        case (.chinese, .target): "目标"
        case (.english, .manualAccount): "Manual Account ID"
        case (.chinese, .manualAccount): "手动输入账号 ID"
        case (.english, .savedContact): "Saved Contact"
        case (.chinese, .savedContact): "已保存联系人"
        case (.english, .sendFile): "Send File"
        case (.chinese, .sendFile): "发送文件"
        case (.english, .fileRequired): "Choose or drop a file first."
        case (.chinese, .fileRequired): "请先选择或拖入文件。"
        case (.english, .targetRequired): "Enter an account ID or choose a contact."
        case (.chinese, .targetRequired): "请输入账号 ID 或选择联系人。"
        case (.english, .noContacts): "No contacts available"
        case (.chinese, .noContacts): "暂无联系人"
        case (.english, .noTasks): "No transfer tasks"
        case (.chinese, .noTasks): "暂无传输任务"
        case (.english, .fileName): "File"
        case (.chinese, .fileName): "文件"
        case (.english, .status): "Status"
        case (.chinese, .status): "状态"
        case (.english, .progress): "Progress"
        case (.chinese, .progress): "进度"
        case (.english, .direction): "Direction"
        case (.chinese, .direction): "方向"
        case (.english, .taskId): "Task ID"
        case (.chinese, .taskId): "任务 ID"
        case (.english, .cancel): "Cancel"
        case (.chinese, .cancel): "取消"
        case (.english, .noIncomingTransfers): "No incoming transfer requests"
        case (.chinese, .noIncomingTransfers): "暂无接收请求"
        case (.english, .incomingRequests): "Incoming Requests"
        case (.chinese, .incomingRequests): "接收请求"
        case (.english, .accept): "Accept"
        case (.chinese, .accept): "接受"
        case (.english, .reject): "Reject"
        case (.chinese, .reject): "拒绝"
        case (.english, .senderDevice): "Sender Device"
        case (.chinese, .senderDevice): "发送设备"
        case (.english, .transferId): "Transfer ID"
        case (.chinese, .transferId): "传输 ID"
        case (.english, .fileSize): "File Size"
        case (.chinese, .fileSize): "文件大小"
        case (.english, .receivedAt): "Received At"
        case (.chinese, .receivedAt): "收到时间"
        case (.english, .blocks): "Blocks"
        case (.chinese, .blocks): "分块数"
        case (.english, .receiverSource): "Receiver Source"
        case (.chinese, .receiverSource): "接收方来源"
        case (.english, .publicKeyText): "Public Key Text"
        case (.chinese, .publicKeyText): "公钥文本"
        case (.english, .publicKeyFile): "Public Key File"
        case (.chinese, .publicKeyFile): "公钥文件"
        case (.english, .inputFile): "Input File"
        case (.chinese, .inputFile): "输入文件"
        case (.english, .outputDirectory): "Output Directory"
        case (.chinese, .outputDirectory): "输出目录"
        case (.english, .chooseFolder): "Choose Folder"
        case (.chinese, .chooseFolder): "选择文件夹"
        case (.english, .encrypt): "Encrypt"
        case (.chinese, .encrypt): "加密"
        case (.english, .decrypt): "Decrypt"
        case (.chinese, .decrypt): "解密"
        case (.english, .inputText): "Input"
        case (.chinese, .inputText): "输入"
        case (.english, .output): "Output"
        case (.chinese, .output): "输出"
        case (.english, .copy): "Copy"
        case (.chinese, .copy): "复制"
        case (.english, .clear): "Clear"
        case (.chinese, .clear): "清空"
        case (.english, .copyPath): "Copy Path"
        case (.chinese, .copyPath): "复制路径"
        case (.english, .revealInFinder): "Reveal in Finder"
        case (.chinese, .revealInFinder): "在 Finder 中显示"
        case (.english, .receiverRequired): "Choose exactly one receiver source."
        case (.chinese, .receiverRequired): "请选择且仅选择一个接收方来源。"
        case (.english, .payloadRequired): "Enter an encrypted payload first."
        case (.chinese, .payloadRequired): "请先输入加密后的 payload。"
        case (.english, .result): "Result"
        case (.chinese, .result): "结果"
        case (.english, .keySetup): "Key Setup"
        case (.chinese, .keySetup): "密钥设置"
        case (.english, .keyMissing): "Private key is missing"
        case (.chinese, .keyMissing): "缺少私钥"
        case (.english, .keyMissingDetail): "Generate a new key, import an existing private key, or skip for now. Key-required actions remain unavailable until a private key exists."
        case (.chinese, .keyMissingDetail): "请生成新密钥、导入已有私钥，或暂时跳过。需要密钥的操作会在私钥存在前保持不可用。"
        case (.english, .generateKey): "Generate New Key Pair"
        case (.chinese, .generateKey): "生成新密钥对"
        case (.english, .importPrivateKey): "Import Private Key"
        case (.chinese, .importPrivateKey): "导入私钥"
        case (.english, .skipForNow): "Skip for now"
        case (.chinese, .skipForNow): "暂时跳过"
        case (.english, .pastePrivateKey): "Paste Private Key"
        case (.chinese, .pastePrivateKey): "粘贴私钥"
        case (.english, .privateKeyFile): "Private Key File"
        case (.chinese, .privateKeyFile): "私钥文件"
        case (.english, .keyStatus): "Key Status"
        case (.chinese, .keyStatus): "密钥状态"
        case (.english, .exportPublicKey): "Export Public Key"
        case (.chinese, .exportPublicKey): "导出公钥"
        case (.english, .exportPrivateKey): "Export Private Key"
        case (.chinese, .exportPrivateKey): "导出私钥"

        case (.english, .qrPreview): "QR Preview"
        case (.chinese, .qrPreview): "二维码预览"
        case (.english, .privateKeyRequired): "Paste a private key or choose/drop a private key file."
        case (.chinese, .privateKeyRequired): "请粘贴私钥或选择/拖入私钥文件。"
        case (.english, .keyGenerated): "Key generated."
        case (.chinese, .keyGenerated): "密钥已生成。"
        case (.english, .keySetupSkipped): "Key setup skipped for now."
        case (.chinese, .keySetupSkipped): "已暂时跳过密钥设置。"
        case (.english, .privateKeyImported): "Private key imported."
        case (.chinese, .privateKeyImported): "私钥已导入。"
        case (.english, .publicKeyExported): "Public key exported."
        case (.chinese, .publicKeyExported): "公钥已导出。"
        case (.english, .privateKeyExported): "Private key exported."
        case (.chinese, .privateKeyExported): "私钥已导出。"
        case (.english, .contacts): "Contacts"
        case (.chinese, .contacts): "联系人"
        case (.english, .blacklist): "Blacklist"
        case (.chinese, .blacklist): "黑名单"
        case (.english, .alias): "Alias"
        case (.chinese, .alias): "备注名"
        case (.english, .publicKey): "Public Key"
        case (.chinese, .publicKey): "公钥"
        case (.english, .publicKeyPath): "Public Key Path"
        case (.chinese, .publicKeyPath): "公钥路径"
        case (.english, .addContact): "Add Contact"
        case (.chinese, .addContact): "添加联系人"
        case (.english, .updateContact): "Update Contact"
        case (.chinese, .updateContact): "更新联系人"
        case (.english, .deleteContact): "Delete Contact"
        case (.chinese, .deleteContact): "删除联系人"
        case (.english, .searchUser): "Search User"
        case (.chinese, .searchUser): "搜索用户"
        case (.english, .searchAndAdd): "Search & Add"
        case (.chinese, .searchAndAdd): "搜索并添加"
        case (.english, .addToBlacklist): "Add to Blacklist"
        case (.chinese, .addToBlacklist): "加入黑名单"
        case (.english, .removeFromBlacklist): "Remove from Blacklist"
        case (.chinese, .removeFromBlacklist): "移出黑名单"
        case (.english, .reason): "Reason"
        case (.chinese, .reason): "原因"
        case (.english, .contactSourceRequired): "Enter an account ID, public key, or public key path."
        case (.chinese, .contactSourceRequired): "请输入账号 ID、公钥或公钥路径。"
        case (.english, .contactAdded): "Contact added."
        case (.chinese, .contactAdded): "联系人已添加。"
        case (.english, .contactUpdated): "Contact updated."
        case (.chinese, .contactUpdated): "联系人已更新。"
        case (.english, .contactRemoved): "Contact removed."
        case (.chinese, .contactRemoved): "联系人已删除。"
        case (.english, .blacklistAdded): "Added to blacklist."
        case (.chinese, .blacklistAdded): "已加入黑名单。"
        case (.english, .blacklistRemoved): "Removed from blacklist."
        case (.chinese, .blacklistRemoved): "已移出黑名单。"
        case (.english, .searchComplete): "Search complete."
        case (.chinese, .searchComplete): "搜索完成。"
        case (.english, .selectContact): "Select a contact"
        case (.chinese, .selectContact): "选择联系人"
        case (.english, .conversations): "Conversations"
        case (.chinese, .conversations): "会话"
        case (.english, .message): "Message"
        case (.chinese, .message): "消息"
        case (.english, .sendMessage): "Send Message"
        case (.chinese, .sendMessage): "发送消息"
        case (.english, .messageRequired): "Enter a message first."
        case (.chinese, .messageRequired): "请先输入消息。"
        case (.english, .messageSent): "Message sent."
        case (.chinese, .messageSent): "消息已发送。"
        case (.english, .noConversations): "No conversations"
        case (.chinese, .noConversations): "暂无会话"
        case (.english, .unread): "Unread"
        case (.chinese, .unread): "未读"
        case (.english, .incomingTransferNotice): "New incoming transfer request"
        case (.chinese, .incomingTransferNotice): "收到新的传输请求"
        case (.english, .incomingMessageNotice): "New incoming message"
        case (.chinese, .incomingMessageNotice): "收到新消息"
        case (.english, .eventStreamConnected): "Live updates connected"
        case (.chinese, .eventStreamConnected): "实时更新已连接"
        case (.english, .eventStreamDisconnected): "Live updates reconnecting"
        case (.chinese, .eventStreamDisconnected): "实时更新重连中"
        case (.english, .relayOverview): "Overview"
        case (.chinese, .relayOverview): "概览"
        case (.english, .relaySend): "Send"
        case (.chinese, .relaySend): "发送"
        case (.english, .relayReceive): "Receive"
        case (.chinese, .relayReceive): "接收"
        case (.english, .relayTasks): "Tasks"
        case (.chinese, .relayTasks): "任务"
        case (.english, .relayMessages): "Messages"
        case (.chinese, .relayMessages): "消息"
        case (.english, .relayContacts): "Contacts"
        case (.chinese, .relayContacts): "联系人"
        case (.english, .fileEncrypt): "File Encrypt"
        case (.chinese, .fileEncrypt): "文件加密"
        case (.english, .fileDecrypt): "File Decrypt"
        case (.chinese, .fileDecrypt): "文件解密"
        case (.english, .textEncrypt): "Text Encrypt"
        case (.chinese, .textEncrypt): "文本加密"
        case (.english, .textDecrypt): "Text Decrypt"
        case (.chinese, .textDecrypt): "文本解密"
        case (.english, .placeholder): "This section is reserved for the next implementation slice."
        case (.chinese, .placeholder): "此区域将在下一个实现切片中完成。"
        case (.english, .lastUpdated): "Last updated"
        case (.chinese, .lastUpdated): "最后更新"
        }
    }

    enum Key {
        case appTitle
        case relayTab
        case offlineTab
        case localService
        case starting
        case unavailable
        case retry
        case openConsole
        case language
        case connected
        case disconnected
        case serviceReady
        case accountId
        case deviceId
        case server
        case host
        case port
        case connect
        case disconnect
        case authenticated
        case notAuthenticated
        case temporarySettings
        case refresh
        case invalidPort
        case chooseFile
        case dropFile
        case selectedFile
        case target
        case manualAccount
        case savedContact
        case sendFile
        case fileRequired
        case targetRequired
        case noContacts
        case noTasks
        case fileName
        case status
        case progress
        case direction
        case taskId
        case cancel
        case noIncomingTransfers
        case incomingRequests
        case accept
        case reject
        case senderDevice
        case transferId
        case fileSize
        case receivedAt
        case blocks
        case receiverSource
        case publicKeyText
        case publicKeyFile
        case inputFile
        case outputDirectory
        case chooseFolder
        case encrypt
        case decrypt
        case inputText
        case output
        case copy
        case clear
        case copyPath
        case revealInFinder
        case receiverRequired
        case payloadRequired
        case result
        case keySetup
        case keyMissing
        case keyMissingDetail
        case generateKey
        case importPrivateKey
        case skipForNow
        case pastePrivateKey
        case privateKeyFile
        case keyStatus
        case exportPublicKey
        case exportPrivateKey
        case qrPreview
        case privateKeyRequired
        case keyGenerated
        case keySetupSkipped
        case privateKeyImported
        case publicKeyExported
        case privateKeyExported
        case contacts
        case blacklist
        case alias
        case publicKey
        case publicKeyPath
        case addContact
        case updateContact
        case deleteContact
        case searchUser
        case searchAndAdd
        case addToBlacklist
        case removeFromBlacklist
        case reason
        case contactSourceRequired
        case contactAdded
        case contactUpdated
        case contactRemoved
        case blacklistAdded
        case blacklistRemoved
        case searchComplete
        case selectContact
        case conversations
        case message
        case sendMessage
        case messageRequired
        case messageSent
        case noConversations
        case unread
        case incomingTransferNotice
        case incomingMessageNotice
        case eventStreamConnected
        case eventStreamDisconnected
        case relayOverview
        case relaySend
        case relayReceive
        case relayTasks
        case relayMessages
        case relayContacts
        case fileEncrypt
        case fileDecrypt
        case textEncrypt
        case textDecrypt
        case placeholder
        case lastUpdated
    }
}
