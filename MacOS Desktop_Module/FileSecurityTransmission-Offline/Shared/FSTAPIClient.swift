import Foundation

enum FSTAPIError: LocalizedError {
    case invalidResponse(URL?)
    case httpStatus(Int, URL?, String)
    case emptyBody(URL?)
    case decoding(URL?, String, String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse(let url):
            return "Invalid response from local service\(Self.pathSuffix(url))."
        case .httpStatus(let status, let url, let body):
            let excerpt = Self.excerpt(body)
            return excerpt.isEmpty
                ? "Local service returned HTTP \(status)\(Self.pathSuffix(url))."
                : "Local service returned HTTP \(status)\(Self.pathSuffix(url)): \(excerpt)"
        case .emptyBody(let url):
            return "Local service returned an empty response\(Self.pathSuffix(url))."
        case .decoding(let url, let detail, let body):
            let excerpt = Self.excerpt(body)
            return excerpt.isEmpty
                ? "Unable to read local service response\(Self.pathSuffix(url)): \(detail)"
                : "Unable to read local service response\(Self.pathSuffix(url)): \(detail). Body: \(excerpt)"
        }
    }

    private static func pathSuffix(_ url: URL?) -> String {
        guard let url else { return "" }
        return " for \(url.path)"
    }

    private static func excerpt(_ body: String) -> String {
        let compact = body
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard compact.count > 360 else {
            return compact
        }
        return String(compact.prefix(360)) + "..."
    }
}

struct SystemStatus: Decodable {
    let application: String?
    let status: String?
    let deviceId: String?
    let accountId: String?
    let clientServerHost: String?
    let clientServerPort: Int?
    let taskCount: Int?
    let localTransferHistoryPath: String?
}

struct LanguageStatus: Decodable {
    let language: String?
    let value: String?
    let settingsPath: String?
}

struct LanguageUpdateResponse: Decodable {
    let success: Bool?
    let language: String?
    let value: String?
    let message: String?
}

struct StartupStatus: Decodable, Hashable {
    let keyMissing: Bool?
    let keySetupPrompted: Bool?
    let shouldPromptKeySetup: Bool?
    let autoConnectEnabled: Bool?
    let autoConnectAttempted: Bool?
    let autoConnectBlocked: Bool?
    let message: String?
}

struct KeyStatus: Decodable, Hashable {
    let hasPrivateKey: Bool?
    let hasPublicKey: Bool?
    let publicKeyFingerprint: String?
    let accountId: String?
    let publicKey: String?
    let message: String?

    var hasUsablePrivateKey: Bool {
        hasPrivateKey == true
    }

    var displayAccountId: String? {
        accountId?.nilIfBlank ?? publicKeyFingerprint?.nilIfBlank
    }

    enum CodingKeys: String, CodingKey {
        case hasPrivateKey
        case hasPublicKey
        case publicKeyFingerprint
        case accountId
        case publicKey
        case message
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        hasPrivateKey = container.decodeBoolIfPresent(.hasPrivateKey)
        hasPublicKey = container.decodeBoolIfPresent(.hasPublicKey)
        publicKeyFingerprint = container.decodeStringIfPresent(.publicKeyFingerprint)
        accountId = container.decodeStringIfPresent(.accountId)
        publicKey = container.decodeStringIfPresent(.publicKey)
        message = container.decodeStringIfPresent(.message)
    }
}

struct KeyExportArtifact: Decodable, Hashable {
    let publicKey: String?
    let privateKey: String?
    let qrText: String?
    let pngPath: String?
    let textPath: String?
    let asciiPath: String?
    let expiresAt: String?

    var keyText: String? {
        publicKey?.nilIfBlank ?? privateKey?.nilIfBlank
    }
}

struct ConnectionStatus: Decodable {
    let deviceId: String?
    let accountId: String?
    let status: String?
    let connected: Bool?
    let authenticated: Bool?
    let connectedHost: String?
    let connectedPort: Int?
}

struct ActionResponse: Decodable {
    let success: Bool?
    let accepted: Bool?
    let message: String?
    let host: String?
    let port: Int?
}

struct ContactSummary: Decodable, Identifiable, Hashable {
    let contactIndex: Int
    let alias: String?
    let accountId: String?
    let publicKey: String?
    let createdAt: String?
    let updatedAt: String?

    var id: Int { contactIndex }

    enum CodingKeys: String, CodingKey {
        case contactIndex
        case alias
        case accountId
        case publicKey
        case createdAt
        case updatedAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        contactIndex = container.decodeIntIfPresent(.contactIndex) ?? -1
        alias = container.decodeStringIfPresent(.alias)
        accountId = container.decodeStringIfPresent(.accountId)
        publicKey = container.decodeStringIfPresent(.publicKey)
        createdAt = container.decodeStringIfPresent(.createdAt)
        updatedAt = container.decodeStringIfPresent(.updatedAt)
    }

    var displayName: String {
        if let alias, !alias.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return alias
        }
        return "contact-\(contactIndex)"
    }
}

struct BlacklistEntry: Decodable, Identifiable, Hashable {
    let accountId: String
    let publicKey: String?
    let reason: String?
    let createdAt: String?

    var id: String { accountId }

    enum CodingKeys: String, CodingKey {
        case accountId
        case publicKey
        case reason
        case createdAt
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        accountId = container.decodeStringIfPresent(.accountId) ?? ""
        publicKey = container.decodeStringIfPresent(.publicKey)
        reason = container.decodeStringIfPresent(.reason)
        createdAt = container.decodeStringIfPresent(.createdAt)
    }
}

struct SearchUserResult: Decodable, Hashable {
    let found: Bool?
    let accountId: String?
    let publicKey: String?
    let message: String?
}

struct MessageConversationSummary: Decodable, Identifiable, Hashable {
    let peerAccountId: String
    let alias: String?
    let unreadCount: Int?
    let lastMessageTime: String?
    let lastDirection: String?
    let lastStatus: String?
    let lastMode: String?

    var id: String { peerAccountId }

    var displayName: String {
        alias?.nilIfBlank ?? peerAccountId
    }

    enum CodingKeys: String, CodingKey {
        case peerAccountId
        case alias
        case unreadCount
        case lastMessageTime
        case lastDirection
        case lastStatus
        case lastMode
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        peerAccountId = container.decodeStringIfPresent(.peerAccountId) ?? ""
        alias = container.decodeStringIfPresent(.alias)
        unreadCount = container.decodeIntIfPresent(.unreadCount)
        lastMessageTime = container.decodeStringIfPresent(.lastMessageTime)
        lastDirection = container.decodeStringIfPresent(.lastDirection)
        lastStatus = container.decodeStringIfPresent(.lastStatus)
        lastMode = container.decodeStringIfPresent(.lastMode)
    }
}

struct TextMessageItem: Decodable, Identifiable, Hashable {
    let messageId: String
    let peerAccountId: String?
    let senderAccountId: String?
    let receiverAccountId: String?
    let direction: String?
    let mode: String?
    let status: String?
    let createdAt: String?
    let receivedAt: String?
    let readAt: String?
    let body: String?
    let errorMessage: String?

    var id: String { messageId }

    var isOutgoing: Bool {
        direction?.lowercased() == "outgoing"
    }

    enum CodingKeys: String, CodingKey {
        case messageId
        case peerAccountId
        case senderAccountId
        case receiverAccountId
        case direction
        case mode
        case status
        case createdAt
        case receivedAt
        case readAt
        case body
        case errorMessage
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        messageId = container.decodeStringIfPresent(.messageId) ?? UUID().uuidString
        peerAccountId = container.decodeStringIfPresent(.peerAccountId)
        senderAccountId = container.decodeStringIfPresent(.senderAccountId)
        receiverAccountId = container.decodeStringIfPresent(.receiverAccountId)
        direction = container.decodeStringIfPresent(.direction)
        mode = container.decodeStringIfPresent(.mode)
        status = container.decodeStringIfPresent(.status)
        createdAt = container.decodeStringIfPresent(.createdAt)
        receivedAt = container.decodeStringIfPresent(.receivedAt)
        readAt = container.decodeStringIfPresent(.readAt)
        body = container.decodeStringIfPresent(.body)
        errorMessage = container.decodeStringIfPresent(.errorMessage)
    }
}

struct SendMessageResponse: Decodable, Hashable {
    let success: Bool?
    let messageId: String?
    let targetAccountId: String?
    let status: String?
}

struct SendFileResponse: Decodable {
    let success: Bool?
    let taskId: String?
    let targetAccountId: String?
    let message: String?
}

struct TransferTaskSummary: Decodable, Identifiable, Hashable {
    let taskId: String?
    let transferId: String?
    let direction: String?
    let status: String?
    let fileName: String?
    let progress: Double?
    let message: String?

    var id: String {
        taskId ?? transferId ?? UUID().uuidString
    }

    enum CodingKeys: String, CodingKey {
        case taskId
        case transferId
        case direction
        case status
        case fileName
        case progress
        case message
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        taskId = container.decodeStringIfPresent(.taskId)
        transferId = container.decodeStringIfPresent(.transferId)
        direction = container.decodeStringIfPresent(.direction)
        status = container.decodeStringIfPresent(.status)
        fileName = container.decodeStringIfPresent(.fileName)
        progress = container.decodeDoubleIfPresent(.progress)
        message = container.decodeStringIfPresent(.message)
    }
}

struct TransferTaskDetail: Decodable, Identifiable, Hashable {
    let taskId: String?
    let transferId: String?
    let direction: String?
    let status: String?
    let fileName: String?
    let progress: Double?
    let message: String?
    let localPath: String?
    let peerDeviceId: String?
    let transferredBytes: Int64?
    let totalBytes: Int64?
    let transferredBlocks: Int?
    let totalBlocks: Int?
    let createdAt: String?
    let transferStartedAt: String?
    let speedMegabytesPerSecond: Double?
    let speedText: String?

    var id: String {
        taskId ?? transferId ?? UUID().uuidString
    }

    enum CodingKeys: String, CodingKey {
        case taskId
        case transferId
        case direction
        case status
        case fileName
        case progress
        case message
        case localPath
        case peerDeviceId
        case transferredBytes
        case totalBytes
        case transferredBlocks
        case totalBlocks
        case createdAt
        case transferStartedAt
        case speedMegabytesPerSecond
        case speedText
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        taskId = container.decodeStringIfPresent(.taskId)
        transferId = container.decodeStringIfPresent(.transferId)
        direction = container.decodeStringIfPresent(.direction)
        status = container.decodeStringIfPresent(.status)
        fileName = container.decodeStringIfPresent(.fileName)
        progress = container.decodeDoubleIfPresent(.progress)
        message = container.decodeStringIfPresent(.message)
        localPath = container.decodeStringIfPresent(.localPath)
        peerDeviceId = container.decodeStringIfPresent(.peerDeviceId)
        transferredBytes = container.decodeInt64IfPresent(.transferredBytes)
        totalBytes = container.decodeInt64IfPresent(.totalBytes)
        transferredBlocks = container.decodeIntIfPresent(.transferredBlocks)
        totalBlocks = container.decodeIntIfPresent(.totalBlocks)
        createdAt = container.decodeStringIfPresent(.createdAt)
        transferStartedAt = container.decodeStringIfPresent(.transferStartedAt)
        speedMegabytesPerSecond = container.decodeDoubleIfPresent(.speedMegabytesPerSecond)
        speedText = container.decodeStringIfPresent(.speedText)
    }
}

struct IncomingTransferRequest: Decodable, Identifiable, Hashable {
    let receivedAt: String?
    let transferId: String
    let senderDeviceId: String?
    let fileName: String?
    let fileSize: Int64?
    let totalBlocks: Int?

    var id: String { transferId }

    enum CodingKeys: String, CodingKey {
        case receivedAt
        case transferId
        case senderDeviceId
        case fileName
        case fileSize
        case totalBlocks
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        receivedAt = container.decodeStringIfPresent(.receivedAt)
        transferId = container.decodeStringIfPresent(.transferId) ?? UUID().uuidString
        senderDeviceId = container.decodeStringIfPresent(.senderDeviceId)
        fileName = container.decodeStringIfPresent(.fileName)
        fileSize = container.decodeInt64IfPresent(.fileSize)
        totalBlocks = container.decodeIntIfPresent(.totalBlocks)
    }
}

struct OfflineFileResult: Decodable, Hashable {
    let success: Bool?
    let outputPath: String?
    let fileName: String?
    let fileSize: Int64?
    let totalBlocks: Int?
}

struct OfflineTextEncryptResult: Decodable, Hashable {
    let success: Bool?
    let payload: String?
    let plaintextLength: Int?
}

struct OfflineTextDecryptResult: Decodable, Hashable {
    let success: Bool?
    let text: String?
    let plaintextLength: Int?
}

struct OfflineReceiverInput: Hashable {
    let contactIndex: Int?
    let receiverPublicKey: String?
    let receiverPublicKeyPath: String?
}

private struct ConnectRequest: Encodable {
    let host: String
    let port: Int
}

private struct SendFileRequest: Encodable {
    let filePath: String
    let targetAccountId: String
}

private struct TransferIdRequest: Encodable {
    let transferId: String
}

private struct ImportPrivateKeyRequest: Encodable {
    let privateKey: String?
    let privateKeyPath: String?
}

private struct ContactRequest: Encodable {
    let accountId: String?
    let alias: String?
    let publicKey: String?
    let publicKeyPath: String?
}

private struct BlacklistRequest: Encodable {
    let accountId: String?
    let publicKey: String?
    let reason: String?
}

private struct SendMessageRequest: Encodable {
    let targetAccountId: String
    let text: String
}

private struct OfflineFileEncryptRequest: Encodable {
    let filePath: String
    let outputDir: String?
    let contactIndex: String?
    let receiverPublicKey: String?
    let receiverPublicKeyPath: String?
}

private struct OfflineFileDecryptRequest: Encodable {
    let fst2Path: String
    let outputDir: String?
}

private struct OfflineTextEncryptRequest: Encodable {
    let text: String
    let contactIndex: String?
    let receiverPublicKey: String?
    let receiverPublicKeyPath: String?
}

private struct OfflineTextDecryptRequest: Encodable {
    let payload: String
}

final class FSTAPIClient {
    private let baseURL: URL
    private let session: URLSession
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init(baseURL: URL = URL(string: "http://127.0.0.1:20201")!, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func systemStatus() async throws -> SystemStatus {
        try await get("/api/system/status")
    }

    func eventStreamURL() -> URL {
        baseURL.appending(path: "/api/events")
    }

    func languageStatus() async throws -> LanguageStatus {
        try await get("/api/system/language")
    }

    func startupStatus() async throws -> StartupStatus {
        try await get("/api/system/startup-status")
    }

    func keyStatus() async throws -> KeyStatus {
        try await get("/api/system/key")
    }

    func generateStartupKey() async throws {
        let _: StartupActionResponse = try await post("/api/system/startup/key/generate", body: EmptyBody())
    }

    func skipStartupKeySetup() async throws -> StartupStatus {
        try await post("/api/system/startup/key/skip", body: EmptyBody())
    }

    func importStartupPrivateKey(privateKey: String?, privateKeyPath: String?) async throws -> StartupStatus {
        try await post("/api/system/startup/key/import-private", body: ImportPrivateKeyRequest(privateKey: privateKey, privateKeyPath: privateKeyPath))
    }

    func exportPublicKey() async throws -> KeyExportArtifact {
        try await post("/api/system/key/export-public", body: EmptyBody())
    }

    func exportPrivateKey() async throws -> KeyExportArtifact {
        try await post("/api/system/key/export-private", body: EmptyBody())
    }

    func updateLanguage(_ language: AppLanguage) async throws -> LanguageUpdateResponse {
        try await post("/api/system/language", body: ["language": language.apiValue])
    }

    func connectionStatus() async throws -> ConnectionStatus {
        try await get("/api/system/connection-status")
    }

    func connect(host: String, port: Int) async throws -> ActionResponse {
        try await post("/api/system/connect", body: ConnectRequest(host: host, port: port))
    }

    func disconnect() async throws -> ActionResponse {
        try await post("/api/system/disconnect", body: EmptyBody())
    }

    func contacts() async throws -> [ContactSummary] {
        try await get("/api/contacts")
    }

    func addContact(accountId: String?, alias: String?, publicKey: String?, publicKeyPath: String?) async throws -> ContactSummary {
        try await post("/api/contacts", body: ContactRequest(accountId: accountId, alias: alias, publicKey: publicKey, publicKeyPath: publicKeyPath))
    }

    func updateContact(contactIndex: Int, alias: String?, publicKey: String?, publicKeyPath: String?) async throws -> ContactSummary {
        try await put("/api/contacts/\(contactIndex)", body: ContactRequest(accountId: nil, alias: alias, publicKey: publicKey, publicKeyPath: publicKeyPath))
    }

    func removeContact(contactIndex: Int) async throws -> ActionResponse {
        try await delete("/api/contacts/\(contactIndex)")
    }

    func searchUser(accountId: String) async throws -> SearchUserResult {
        try await get("/api/contacts/search-user/\(accountId)")
    }

    func searchUserAndAddContact(accountId: String, alias: String?) async throws -> ContactSummary {
        try await post("/api/contacts/search-user-add", body: ContactRequest(accountId: accountId, alias: alias, publicKey: nil, publicKeyPath: nil))
    }

    func blacklist() async throws -> [BlacklistEntry] {
        try await get("/api/contacts/blacklist")
    }

    func addBlacklist(accountId: String, publicKey: String?, reason: String?) async throws -> ActionResponse {
        try await post("/api/contacts/blacklist", body: BlacklistRequest(accountId: accountId, publicKey: publicKey, reason: reason))
    }

    func addBlacklist(contactIndex: Int, reason: String?) async throws -> ActionResponse {
        try await post("/api/contacts/blacklist/contact/\(contactIndex)", body: BlacklistRequest(accountId: nil, publicKey: nil, reason: reason))
    }

    func removeBlacklist(accountId: String) async throws -> ActionResponse {
        try await delete("/api/contacts/blacklist/\(accountId)")
    }

    func messageSummaries() async throws -> [MessageConversationSummary] {
        try await get("/api/messages")
    }

    func conversation(accountId: String) async throws -> [TextMessageItem] {
        try await get("/api/messages/\(accountId)")
    }

    func sendMessage(targetAccountId: String, text: String) async throws -> SendMessageResponse {
        try await post("/api/messages/send", body: SendMessageRequest(targetAccountId: targetAccountId, text: text))
    }

    func sendFile(filePath: String, targetAccountId: String) async throws -> SendFileResponse {
        try await post("/api/send", body: SendFileRequest(filePath: filePath, targetAccountId: targetAccountId))
    }

    func transferTasks() async throws -> [TransferTaskSummary] {
        try await get("/api/send/tasks")
    }

    func transferTaskDetail(taskIdOrTransferId: String) async throws -> TransferTaskDetail {
        try await get("/api/send/tasks/\(taskIdOrTransferId)")
    }

    func transferTaskEventStreamURL(taskIdOrTransferId: String) -> URL {
        baseURL.appending(path: "/api/send/tasks/\(taskIdOrTransferId)/events")
    }

    func cancelTransfer(taskIdOrTransferId: String) async throws -> ActionResponse {
        try await post("/api/send/tasks/\(taskIdOrTransferId)/cancel", body: EmptyBody())
    }

    func incomingTransfers() async throws -> [IncomingTransferRequest] {
        try await get("/api/receive/incoming")
    }

    func acceptIncomingTransfer(transferId: String) async throws -> ActionResponse {
        try await post("/api/receive/accept", body: TransferIdRequest(transferId: transferId))
    }

    func rejectIncomingTransfer(transferId: String) async throws -> ActionResponse {
        try await post("/api/receive/reject", body: TransferIdRequest(transferId: transferId))
    }

    func encryptOfflineFile(filePath: String, outputDir: String?, receiver: OfflineReceiverInput) async throws -> OfflineFileResult {
        try await post("/api/offline/files/encrypt", body: OfflineFileEncryptRequest(
            filePath: filePath,
            outputDir: outputDir,
            contactIndex: receiver.contactIndex.map(String.init),
            receiverPublicKey: receiver.receiverPublicKey,
            receiverPublicKeyPath: receiver.receiverPublicKeyPath
        ))
    }

    func decryptOfflineFile(fst2Path: String, outputDir: String?) async throws -> OfflineFileResult {
        try await post("/api/offline/files/decrypt", body: OfflineFileDecryptRequest(fst2Path: fst2Path, outputDir: outputDir))
    }

    func encryptOfflineText(text: String, receiver: OfflineReceiverInput) async throws -> OfflineTextEncryptResult {
        try await post("/api/offline/text/encrypt", body: OfflineTextEncryptRequest(
            text: text,
            contactIndex: receiver.contactIndex.map(String.init),
            receiverPublicKey: receiver.receiverPublicKey,
            receiverPublicKeyPath: receiver.receiverPublicKeyPath
        ))
    }

    func decryptOfflineText(payload: String) async throws -> OfflineTextDecryptResult {
        try await post("/api/offline/text/decrypt", body: OfflineTextDecryptRequest(payload: payload))
    }

    private func get<T: Decodable>(_ path: String) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "GET"
        return try await send(request)
    }

    private func post<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)
        return try await send(request)
    }

    private func put<T: Decodable, Body: Encodable>(_ path: String, body: Body) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "PUT"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try encoder.encode(body)
        return try await send(request)
    }

    private func delete<T: Decodable>(_ path: String) async throws -> T {
        var request = URLRequest(url: baseURL.appending(path: path))
        request.httpMethod = "DELETE"
        return try await send(request)
    }

    private func send<T: Decodable>(_ request: URLRequest) async throws -> T {
        let (data, response) = try await session.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw FSTAPIError.invalidResponse(request.url)
        }
        guard (200..<300).contains(httpResponse.statusCode) else {
            throw FSTAPIError.httpStatus(httpResponse.statusCode, request.url, String(data: data, encoding: .utf8) ?? "")
        }
        guard !data.isEmpty else {
            throw FSTAPIError.emptyBody(request.url)
        }
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw FSTAPIError.decoding(request.url, Self.decodingDetail(error), String(data: data, encoding: .utf8) ?? "")
        }
    }

    private static func decodingDetail(_ error: Error) -> String {
        guard let decodingError = error as? DecodingError else {
            return error.localizedDescription
        }

        func path(_ codingPath: [CodingKey]) -> String {
            let value = codingPath.map(\.stringValue).joined(separator: ".")
            return value.isEmpty ? "<root>" : value
        }

        switch decodingError {
        case .keyNotFound(let key, let context):
            return "missing key '\(key.stringValue)' at \(path(context.codingPath))"
        case .typeMismatch(let type, let context):
            return "type mismatch for \(type) at \(path(context.codingPath)): \(context.debugDescription)"
        case .valueNotFound(let type, let context):
            return "missing value for \(type) at \(path(context.codingPath)): \(context.debugDescription)"
        case .dataCorrupted(let context):
            return "data corrupted at \(path(context.codingPath)): \(context.debugDescription)"
        @unknown default:
            return decodingError.localizedDescription
        }
    }
}

private struct EmptyBody: Encodable {}

private struct StartupActionResponse: Decodable {}

private enum JSONScalar: Decodable {
    case string(String)
    case int(Int)
    case int64(Int64)
    case double(Double)
    case bool(Bool)
    case array([JSONScalar])
    case object([String: JSONScalar])
    case null

    init(from decoder: Decoder) throws {
        let single = try decoder.singleValueContainer()
        if single.decodeNil() {
            self = .null
        } else if let value = try? single.decode(String.self) {
            self = .string(value)
        } else if let value = try? single.decode(Int.self) {
            self = .int(value)
        } else if let value = try? single.decode(Int64.self) {
            self = .int64(value)
        } else if let value = try? single.decode(Double.self) {
            self = .double(value)
        } else if let value = try? single.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? single.decode([JSONScalar].self) {
            self = .array(value)
        } else {
            self = .object((try? single.decode([String: JSONScalar].self)) ?? [:])
        }
    }

    var stringValue: String? {
        switch self {
        case .string(let value):
            return value
        case .int(let value):
            return String(value)
        case .int64(let value):
            return String(value)
        case .double(let value):
            return value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int64(value)) : String(value)
        case .bool(let value):
            return String(value)
        case .array(let values):
            return values.compactMap(\.stringValue).joined(separator: "-")
        case .object(let values):
            let pairs = values
                .sorted { $0.key < $1.key }
                .map { "\($0.key):\($0.value.stringValue ?? "")" }
            return pairs.joined(separator: ", ")
        case .null:
            return nil
        }
    }
}

private extension KeyedDecodingContainer {
    func decodeStringIfPresent(_ key: Key) -> String? {
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return value
        }
        if let scalar = try? decode(JSONScalar.self, forKey: key) {
            return scalar.stringValue
        }
        return nil
    }

    func decodeIntIfPresent(_ key: Key) -> Int? {
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return Int(value)
        }
        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return Int(value)
        }
        return nil
    }

    func decodeInt64IfPresent(_ key: Key) -> Int64? {
        if let value = try? decodeIfPresent(Int64.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return Int64(value)
        }
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return Int64(value)
        }
        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return Int64(value)
        }
        return nil
    }

    func decodeDoubleIfPresent(_ key: Key) -> Double? {
        if let value = try? decodeIfPresent(Double.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return Double(value)
        }
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            return Double(value)
        }
        return nil
    }

    func decodeBoolIfPresent(_ key: Key) -> Bool? {
        if let value = try? decodeIfPresent(Bool.self, forKey: key) {
            return value
        }
        if let value = try? decodeIfPresent(String.self, forKey: key) {
            switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "true", "yes", "1":
                return true
            case "false", "no", "0":
                return false
            default:
                return nil
            }
        }
        if let value = try? decodeIfPresent(Int.self, forKey: key) {
            return value != 0
        }
        return nil
    }
}
