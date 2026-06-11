import Combine
import Foundation

@MainActor
final class AppViewModel: ObservableObject {
    enum ServiceState: Equatable {
        case starting
        case ready
        case unavailable(String)
    }

    @Published private(set) var serviceState: ServiceState = .starting
    @Published private(set) var systemStatus: SystemStatus?
    @Published private(set) var startupStatus: StartupStatus?
    @Published private(set) var keyStatus: KeyStatus?
    @Published private(set) var keyExportArtifact: KeyExportArtifact?
    @Published private(set) var connectionStatus: ConnectionStatus?
    @Published var language: AppLanguage = .english
    @Published var relayHost: String = ""
    @Published var relayPort: String = ""
    @Published private(set) var contacts: [ContactSummary] = []
    @Published private(set) var blacklistEntries: [BlacklistEntry] = []
    @Published private(set) var searchUserResult: SearchUserResult?
    @Published private(set) var messageSummaries: [MessageConversationSummary] = []
    @Published private(set) var currentConversation: [TextMessageItem] = []
    @Published private(set) var transferTasks: [TransferTaskSummary] = []
    @Published private(set) var selectedTransferTaskDetail: TransferTaskDetail?
    @Published private(set) var taskDetailStreamConnected = false
    @Published private(set) var incomingTransfers: [IncomingTransferRequest] = []
    @Published private(set) var offlineFileResult: OfflineFileResult?
    @Published private(set) var offlineTextOutput: String = ""
    @Published private(set) var operationMessage: String?
    @Published private(set) var keyOperationMessage: String?
    @Published private(set) var inAppNotification: String?
    @Published private(set) var eventStreamConnected = false
    @Published private(set) var operationInProgress = false
    @Published private(set) var lastUpdated: Date?

    private let apiClient: FSTAPIClient
    private var eventStreamTask: Task<Void, Never>?
    private var taskDetailStreamTask: Task<Void, Never>?

    init() {
        self.apiClient = FSTAPIClient()
    }

    init(apiClient: FSTAPIClient) {
        self.apiClient = apiClient
    }

    var strings: AppStrings {
        AppStrings(language: language)
    }

    func bootstrap() async {
        await refreshLanguage()
        await refreshStatus(showStarting: true)
    }

    func refreshStatus(showStarting: Bool = false) async {
        if showStarting {
            serviceState = .starting
        }
        do {
            let status = try await apiClient.systemStatus()
            systemStatus = status
            applyDefaultRelayEndpoint(from: status)
            startupStatus = try? await apiClient.startupStatus()
            keyStatus = try? await apiClient.keyStatus()
            connectionStatus = try? await apiClient.connectionStatus()
            contacts = (try? await apiClient.contacts()) ?? contacts
            blacklistEntries = (try? await apiClient.blacklist()) ?? blacklistEntries
            messageSummaries = (try? await apiClient.messageSummaries()) ?? messageSummaries
            transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
            incomingTransfers = (try? await apiClient.incomingTransfers()) ?? incomingTransfers
            serviceState = .ready
            startEventStream()
            lastUpdated = Date()
        } catch {
            stopEventStream()
            serviceState = .unavailable(error.localizedDescription)
        }
    }

    func refreshLanguage() async {
        do {
            let status = try await apiClient.languageStatus()
            language = AppLanguage(apiValue: status.value ?? status.language)
        } catch {
            language = .english
        }
    }

    func setLanguage(_ newLanguage: AppLanguage) async {
        do {
            _ = try await apiClient.updateLanguage(newLanguage)
            language = newLanguage
        } catch {
            serviceState = .unavailable(error.localizedDescription)
        }
    }

    var isPrivateKeyMissing: Bool {
        if let keyStatus {
            return !keyStatus.hasUsablePrivateKey
        }
        return startupStatus?.keyMissing == true
    }

    func refreshKeyState() async {
        do {
            startupStatus = try await apiClient.startupStatus()
            keyStatus = try await apiClient.keyStatus()
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func generateStartupKey() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            try await apiClient.generateStartupKey()
            startupStatus = try? await apiClient.startupStatus()
            keyStatus = try await apiClient.keyStatus()
            systemStatus = try? await apiClient.systemStatus()
            keyOperationMessage = strings.text(.keyGenerated)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func generateKeyPair() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            try await apiClient.generateKeyPair()
            startupStatus = try? await apiClient.startupStatus()
            keyStatus = try await apiClient.keyStatus()
            systemStatus = try? await apiClient.systemStatus()
            keyExportArtifact = nil
            keyOperationMessage = strings.text(.keyGenerated)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func skipStartupKeySetup() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            startupStatus = try await apiClient.skipStartupKeySetup()
            keyStatus = try? await apiClient.keyStatus()
            keyOperationMessage = strings.text(.keySetupSkipped)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func importStartupPrivateKey(privateKey: String?, privateKeyPath: String?) async {
        let text = privateKey?.nilIfBlank
        let path = privateKeyPath?.nilIfBlank
        guard text != nil || path != nil else {
            keyOperationMessage = strings.text(.privateKeyRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            startupStatus = try await apiClient.importStartupPrivateKey(privateKey: text, privateKeyPath: path)
            keyStatus = try? await apiClient.keyStatus()
            systemStatus = try? await apiClient.systemStatus()
            keyOperationMessage = strings.text(.privateKeyImported)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func importPrivateKey(privateKey: String?, privateKeyPath: String?) async {
        let text = privateKey?.nilIfBlank
        let path = privateKeyPath?.nilIfBlank
        guard text != nil || path != nil else {
            keyOperationMessage = strings.text(.privateKeyRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            keyStatus = try await apiClient.importPrivateKey(privateKey: text, privateKeyPath: path)
            startupStatus = try? await apiClient.startupStatus()
            systemStatus = try? await apiClient.systemStatus()
            keyExportArtifact = nil
            keyOperationMessage = strings.text(.privateKeyImported)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func exportPublicKey() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            keyExportArtifact = try await apiClient.exportPublicKey()
            keyOperationMessage = strings.text(.publicKeyExported)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func exportPrivateKey() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            keyExportArtifact = try await apiClient.exportPrivateKey()
            keyOperationMessage = strings.text(.privateKeyExported)
            lastUpdated = Date()
        } catch {
            keyOperationMessage = error.localizedDescription
        }
    }

    func refreshConnectionStatus() async {
        do {
            connectionStatus = try await apiClient.connectionStatus()
            operationMessage = nil
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func connectRelay() async {
        guard let port = Int(relayPort.trimmingCharacters(in: .whitespacesAndNewlines)), (1...65535).contains(port) else {
            operationMessage = strings.text(.invalidPort)
            return
        }

        let host = relayHost.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !host.isEmpty else {
            operationMessage = "Host is required."
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.connect(host: host, port: port)
            operationMessage = response.message ?? "Connected."
            connectionStatus = try? await apiClient.connectionStatus()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
            connectionStatus = try? await apiClient.connectionStatus()
        }
    }

    func disconnectRelay() async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.disconnect()
            operationMessage = response.message ?? "Disconnected."
            connectionStatus = try? await apiClient.connectionStatus()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func refreshContacts() async {
        do {
            contacts = try await apiClient.contacts()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func refreshBlacklist() async {
        do {
            blacklistEntries = try await apiClient.blacklist()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func addContact(accountId: String, alias: String, publicKey: String, publicKeyPath: String) async {
        let account = accountId.nilIfBlank
        let keyText = publicKey.nilIfBlank
        let keyPath = publicKeyPath.nilIfBlank
        guard account != nil || keyText != nil || keyPath != nil else {
            operationMessage = strings.text(.contactSourceRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            _ = try await apiClient.addContact(accountId: account, alias: alias.nilIfBlank, publicKey: keyText, publicKeyPath: keyPath)
            contacts = try await apiClient.contacts()
            operationMessage = strings.text(.contactAdded)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func updateContact(_ contact: ContactSummary, alias: String, publicKey: String, publicKeyPath: String) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            _ = try await apiClient.updateContact(
                contactIndex: contact.contactIndex,
                alias: alias.nilIfBlank,
                publicKey: publicKey.nilIfBlank,
                publicKeyPath: publicKeyPath.nilIfBlank
            )
            contacts = try await apiClient.contacts()
            operationMessage = strings.text(.contactUpdated)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func removeContact(_ contact: ContactSummary) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.removeContact(contactIndex: contact.contactIndex)
            contacts = try await apiClient.contacts()
            operationMessage = response.message ?? strings.text(.contactRemoved)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func searchUser(accountId: String) async {
        guard let account = accountId.nilIfBlank else {
            operationMessage = strings.text(.targetRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            searchUserResult = try await apiClient.searchUser(accountId: account)
            operationMessage = searchUserResult?.message ?? strings.text(.searchComplete)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func searchUserAndAddContact(accountId: String, alias: String) async {
        guard let account = accountId.nilIfBlank else {
            operationMessage = strings.text(.targetRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            _ = try await apiClient.searchUserAndAddContact(accountId: account, alias: alias.nilIfBlank)
            contacts = try await apiClient.contacts()
            operationMessage = strings.text(.contactAdded)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func addBlacklist(accountId: String, publicKey: String, reason: String) async {
        guard let account = accountId.nilIfBlank else {
            operationMessage = strings.text(.targetRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.addBlacklist(accountId: account, publicKey: publicKey.nilIfBlank, reason: reason.nilIfBlank)
            blacklistEntries = try await apiClient.blacklist()
            operationMessage = response.message ?? strings.text(.blacklistAdded)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func addBlacklist(contact: ContactSummary, reason: String) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.addBlacklist(contactIndex: contact.contactIndex, reason: reason.nilIfBlank)
            blacklistEntries = try await apiClient.blacklist()
            operationMessage = response.message ?? strings.text(.blacklistAdded)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func removeBlacklist(_ entry: BlacklistEntry) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.removeBlacklist(accountId: entry.accountId)
            blacklistEntries = try await apiClient.blacklist()
            operationMessage = response.message ?? strings.text(.blacklistRemoved)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func refreshMessageSummaries() async {
        do {
            messageSummaries = try await apiClient.messageSummaries()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func loadConversation(peerAccountId: String) async {
        guard let peer = peerAccountId.nilIfBlank else {
            return
        }

        do {
            currentConversation = try await apiClient.conversation(accountId: peer)
            messageSummaries = (try? await apiClient.messageSummaries()) ?? messageSummaries
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func sendRelayMessage(targetAccountId: String, text: String) async {
        guard let target = targetAccountId.nilIfBlank else {
            operationMessage = strings.text(.targetRequired)
            return
        }
        guard let body = text.nilIfBlank else {
            operationMessage = strings.text(.messageRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            _ = try await apiClient.sendMessage(targetAccountId: target, text: body)
            currentConversation = try await apiClient.conversation(accountId: target)
            messageSummaries = (try? await apiClient.messageSummaries()) ?? messageSummaries
            operationMessage = strings.text(.messageSent)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func sendRelayFile(filePath: String, targetAccountId: String) async {
        let file = filePath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !file.isEmpty else {
            operationMessage = strings.text(.fileRequired)
            return
        }
        let target = targetAccountId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !target.isEmpty else {
            operationMessage = strings.text(.targetRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.sendFile(filePath: file, targetAccountId: target)
            let task = response.taskId.map { " Task: \($0)" } ?? ""
            operationMessage = (response.message ?? "Send task created.") + task
            transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
            connectionStatus = try? await apiClient.connectionStatus()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func refreshTransferTasks() async {
        do {
            transferTasks = try await apiClient.transferTasks()
            if let selectedTransferTaskDetail,
               let id = selectedTransferTaskDetail.taskId ?? selectedTransferTaskDetail.transferId,
               let updated = try? await apiClient.transferTaskDetail(taskIdOrTransferId: id) {
                self.selectedTransferTaskDetail = updated
            }
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func watchTransferTask(_ task: TransferTaskSummary) async {
        guard let id = task.taskId ?? task.transferId else {
            operationMessage = "Task ID is unavailable."
            return
        }
        stopTransferTaskDetailStream()
        do {
            selectedTransferTaskDetail = try await apiClient.transferTaskDetail(taskIdOrTransferId: id)
            startTransferTaskDetailStream(taskIdOrTransferId: id)
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func closeTransferTaskDetail() {
        stopTransferTaskDetailStream()
        selectedTransferTaskDetail = nil
    }

    func cancelTransfer(_ task: TransferTaskSummary) async {
        guard let id = task.taskId ?? task.transferId else {
            return
        }
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.cancelTransfer(taskIdOrTransferId: id)
            operationMessage = response.message ?? "Transfer canceled."
            transferTasks = try await apiClient.transferTasks()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func refreshIncomingTransfers() async {
        do {
            incomingTransfers = try await apiClient.incomingTransfers()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func acceptIncomingTransfer(_ request: IncomingTransferRequest) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.acceptIncomingTransfer(transferId: request.transferId)
            operationMessage = response.message ?? "Incoming transfer accepted."
            incomingTransfers = try await apiClient.incomingTransfers()
            transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func rejectIncomingTransfer(_ request: IncomingTransferRequest) async {
        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let response = try await apiClient.rejectIncomingTransfer(transferId: request.transferId)
            operationMessage = response.message ?? "Incoming transfer rejected."
            incomingTransfers = try await apiClient.incomingTransfers()
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func encryptOfflineFile(filePath: String, outputDir: String?, receiver: OfflineReceiverInput?) async {
        let file = filePath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !file.isEmpty else {
            operationMessage = strings.text(.fileRequired)
            return
        }
        guard let receiver else {
            operationMessage = strings.text(.receiverRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let result = try await apiClient.encryptOfflineFile(filePath: file, outputDir: outputDir?.nilIfBlank, receiver: receiver)
            offlineFileResult = result
            operationMessage = result.success == false ? "File encryption failed." : "File encrypted."
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func decryptOfflineFile(filePath: String, outputDir: String?) async {
        let file = filePath.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !file.isEmpty else {
            operationMessage = strings.text(.fileRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let result = try await apiClient.decryptOfflineFile(fst2Path: file, outputDir: outputDir?.nilIfBlank)
            offlineFileResult = result
            operationMessage = result.success == false ? "File decryption failed." : "File decrypted."
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func encryptOfflineText(text: String, receiver: OfflineReceiverInput?) async {
        guard let receiver else {
            operationMessage = strings.text(.receiverRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let result = try await apiClient.encryptOfflineText(text: text, receiver: receiver)
            offlineTextOutput = result.payload ?? ""
            operationMessage = result.success == false ? "Text encryption failed." : "Text encrypted."
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func decryptOfflineText(payload: String) async {
        let value = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            operationMessage = strings.text(.payloadRequired)
            return
        }

        operationInProgress = true
        defer { operationInProgress = false }

        do {
            let result = try await apiClient.decryptOfflineText(payload: value)
            offlineTextOutput = result.text ?? ""
            operationMessage = result.success == false ? "Text decryption failed." : "Text decrypted."
            lastUpdated = Date()
        } catch {
            operationMessage = error.localizedDescription
        }
    }

    func clearOfflineTextOutput() {
        offlineTextOutput = ""
        operationMessage = nil
    }

    func clearOfflineFileResult() {
        offlineFileResult = nil
        operationMessage = nil
    }

    func dismissInAppNotification() {
        inAppNotification = nil
    }

    private func startEventStream() {
        guard eventStreamTask == nil else {
            return
        }

        let url = apiClient.eventStreamURL()
        eventStreamTask = Task { [weak self] in
            while !Task.isCancelled {
                await self?.consumeEventStream(url: url)
                if !Task.isCancelled {
                    await self?.handleEventStreamDisconnected()
                    try? await Task.sleep(for: .seconds(2))
                }
            }
        }
    }

    private func stopEventStream() {
        eventStreamTask?.cancel()
        eventStreamTask = nil
        eventStreamConnected = false
    }

    private func startTransferTaskDetailStream(taskIdOrTransferId: String) {
        let url = apiClient.transferTaskEventStreamURL(taskIdOrTransferId: taskIdOrTransferId)
        taskDetailStreamTask = Task { [weak self] in
            await self?.consumeTaskDetailStream(url: url)
        }
    }

    private func stopTransferTaskDetailStream() {
        taskDetailStreamTask?.cancel()
        taskDetailStreamTask = nil
        taskDetailStreamConnected = false
    }

    private func consumeTaskDetailStream(url: URL) async {
        do {
            let (bytes, response) = try await URLSession.shared.bytes(from: url)
            guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
                throw FSTAPIError.invalidResponse(url)
            }
            taskDetailStreamConnected = true

            var dataLines: [String] = []
            for try await rawLine in bytes.lines {
                if Task.isCancelled {
                    break
                }
                let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
                if line.isEmpty {
                    if !dataLines.isEmpty {
                        await applyTaskDetailEvent(dataLines.joined(separator: "\n"))
                    }
                    dataLines.removeAll()
                    continue
                }
                if line.hasPrefix("data:") {
                    dataLines.append(String(line.dropFirst("data:".count)).trimmingCharacters(in: .whitespacesAndNewlines))
                }
            }
        } catch {
            if !Task.isCancelled {
                taskDetailStreamConnected = false
            }
        }
    }

    private func applyTaskDetailEvent(_ dataText: String) async {
        guard let data = dataText.data(using: .utf8),
              let detail = try? JSONDecoder().decode(TransferTaskDetail.self, from: data) else {
            return
        }
        selectedTransferTaskDetail = detail
        transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
        lastUpdated = Date()
        if isTerminalStatus(detail.status) {
            taskDetailStreamConnected = false
        }
    }

    private func consumeEventStream(url: URL) async {
        do {
            let (bytes, response) = try await URLSession.shared.bytes(from: url)
            guard let httpResponse = response as? HTTPURLResponse, (200..<300).contains(httpResponse.statusCode) else {
                throw FSTAPIError.invalidResponse(url)
            }

            eventStreamConnected = true
            await resyncAfterEventReconnect()

            var currentEvent: String?
            for try await rawLine in bytes.lines {
                if Task.isCancelled {
                    break
                }

                let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
                if line.isEmpty {
                    if let event = currentEvent {
                        await handleServerEvent(event)
                    }
                    currentEvent = nil
                    continue
                }

                if line.hasPrefix("event:") {
                    currentEvent = String(line.dropFirst("event:".count)).trimmingCharacters(in: .whitespacesAndNewlines)
                }
            }
        } catch {
            if !Task.isCancelled {
                eventStreamConnected = false
            }
        }
    }

    private func handleEventStreamDisconnected() async {
        eventStreamConnected = false
        await resyncAfterEventReconnect()
    }

    private func resyncAfterEventReconnect() async {
        incomingTransfers = (try? await apiClient.incomingTransfers()) ?? incomingTransfers
        messageSummaries = (try? await apiClient.messageSummaries()) ?? messageSummaries
        transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
        connectionStatus = try? await apiClient.connectionStatus()
        lastUpdated = Date()
    }

    private func handleServerEvent(_ event: String) async {
        switch event {
        case "connected":
            eventStreamConnected = true
        case "incoming-transfer-request":
            inAppNotification = strings.text(.incomingTransferNotice)
            incomingTransfers = (try? await apiClient.incomingTransfers()) ?? incomingTransfers
        case "incoming-text-message":
            inAppNotification = strings.text(.incomingMessageNotice)
            messageSummaries = (try? await apiClient.messageSummaries()) ?? messageSummaries
        case let transferEvent where transferEvent.hasPrefix("transfer-"):
            transferTasks = (try? await apiClient.transferTasks()) ?? transferTasks
        case let connectionEvent where connectionEvent.hasPrefix("client-"):
            connectionStatus = try? await apiClient.connectionStatus()
        default:
            break
        }
        lastUpdated = Date()
    }

    private func applyDefaultRelayEndpoint(from status: SystemStatus) {
        if relayHost.isEmpty {
            relayHost = status.clientServerHost ?? ""
        }
        if relayPort.isEmpty, let port = status.clientServerPort {
            relayPort = String(port)
        }
    }

    private func isTerminalStatus(_ status: String?) -> Bool {
        let value = status?.lowercased() ?? ""
        return value.contains("complete") || value.contains("fail") || value.contains("cancel") || value.contains("reject")
    }
}
