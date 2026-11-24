import Foundation

struct HTTPError: LocalizedError {
    let statusCode: Int
    let body: String

    var errorDescription: String? {
        if body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "HTTP \(statusCode)"
        }
        return "HTTP \(statusCode): \(body)"
    }
}

struct ConsoleAPI {
    var baseURL = URL(string: "http://127.0.0.1:20201")!
    private let session = URLSession.shared

    func get(_ path: String) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "GET"
        return try await send(request)
    }

    func post(_ path: String, body: [String: Any] = [:]) async throws -> Data {
        try await requestWithJSON("POST", path: path, body: body)
    }

    func put(_ path: String, body: [String: Any] = [:]) async throws -> Data {
        try await requestWithJSON("PUT", path: path, body: body)
    }

    func delete(_ path: String) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = "DELETE"
        return try await send(request)
    }

    private func requestWithJSON(_ method: String, path: String, body: [String: Any]) async throws -> Data {
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        return try await send(request)
    }

    private func send(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw URLError(.badServerResponse)
        }
        guard (200..<300).contains(http.statusCode) else {
            throw HTTPError(statusCode: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }
        return data
    }
}

@main
struct FSTConsole {
    static func main() async {
        var console = ConsoleSession()
        await console.run()
    }
}

struct ConsoleSession {
    private var api = ConsoleAPI()

    mutating func run() async {
        print("File Security Transmission Console")
        print("HTTP service: \(api.baseURL.absoluteString)")
        print("Type 'help' for commands, 'exit' to quit.")

        while true {
            print("")
            print("fst> ", terminator: "")
            guard let line = readLine() else {
                break
            }

            do {
                let shouldContinue = try await handle(line)
                if !shouldContinue {
                    break
                }
            } catch {
                print("Error: \(error.localizedDescription)")
            }
        }
    }

    private mutating func handle(_ line: String) async throws -> Bool {
        let parts = tokenize(line)
        guard let command = parts.first?.lowercased() else {
            return true
        }
        let args = Array(parts.dropFirst())

        switch command {
        case "exit", "quit":
            return false
        case "help", "?":
            printHelp()
        case "raw":
            try await raw(args)
        case "base-url":
            try setBaseURL(args)
        case "status":
            try await printKeyValues(api.get("api/system/status"))
        case "startup-status":
            try await printKeyValues(api.get("api/system/startup-status"))
        case "key", "key-info":
            try await printKeyValues(api.get("api/system/key"))
        case "generate-key":
            try await printKeyValues(api.post("api/system/key/generate"))
        case "delete-key":
            try await printKeyValues(api.post("api/system/key/delete"))
        case "skip-key-setup":
            try await printKeyValues(api.post("api/system/startup/key/skip"))
        case "import-private-key":
            try require(args, 1, "import-private-key <private-key-text-or-path>")
            let value = args.joined(separator: " ")
            let body: [String: Any] = FileManager.default.fileExists(atPath: value)
                ? ["privateKeyPath": value]
                : ["privateKey": value]
            try await printKeyValues(api.post("api/system/key/import-private", body: body))
        case "import-private-key-file":
            try require(args, 1, "import-private-key-file <path>")
            try await printKeyValues(api.post("api/system/key/import-private-file", body: ["privateKeyPath": args.joined(separator: " ")]))
        case "import-private-key-paste":
            let pasted = readMultilineUntilDot(prompt: "private-key")
            try await printKeyValues(api.post("api/system/key/import-private", body: ["privateKey": pasted]))
        case "export-public-key":
            try await printKeyValues(api.post("api/system/key/export-public"))
        case "export-private-key":
            try await printKeyValues(api.post("api/system/key/export-private"))
        case "public-key":
            try await printKeyValues(api.get("api/system/public-key"))
        case "public-key-fingerprint", "accountid", "account-id":
            if args.isEmpty {
                try await printKeyValues(api.get("api/system/account-id"))
            } else {
                try await printKeyValues(api.post("api/system/account-id", body: ["publicKey": args.joined(separator: " ")]))
            }
        case "language":
            try await language(args)
        case "connection-status":
            try await printKeyValues(api.get("api/system/connection-status"))
        case "connect":
            try await connect(args)
        case "disconnect":
            try await printKeyValues(api.post("api/system/disconnect"))
        case "contacts":
            try await printTable(api.get("api/contacts"), preferredColumns: ["contactIndex", "alias", "accountId", "createdAt", "updatedAt"])
        case "contact-add":
            try await addContact(args)
        case "contact-add-public-key":
            try await addContactWithPublicKey(args)
        case "contact-update":
            try await updateContact(args)
        case "contact-update-public-key":
            try await updateContactPublicKey(args)
        case "contact-delete", "contact-remove":
            try require(args, 1, "\(command) <contact-index>")
            try await printJSON(api.delete("api/contacts/\(encode(contactIndex(args[0])))"))
        case "contact-show":
            try require(args, 1, "contact-show <contact-index>")
            try await printContact(indexText: args[0])
        case "contact-search", "search-user":
            try require(args, 1, "\(command) <account-id>")
            try await printKeyValues(api.get("api/contacts/search-user/\(encode(args[0]))"))
        case "contact-search-add", "search-user-add":
            try require(args, 1, "\(command) <account-id> [alias]")
            try await printKeyValues(api.post("api/contacts/search-user-add", body: [
                "accountId": args[0],
                "alias": args.dropFirst().joined(separator: " ")
            ].compactValues()))
        case "blacklist":
            try await printTable(api.get("api/contacts/blacklist"), preferredColumns: ["accountId", "reason", "createdAt"])
        case "blacklist-add":
            try require(args, 1, "blacklist-add <account-id> [reason]")
            try await printKeyValues(api.post("api/contacts/blacklist", body: [
                "accountId": args[0],
                "reason": args.dropFirst().joined(separator: " ")
            ].compactValues()))
        case "blacklist-add-contact":
            try require(args, 1, "blacklist-add-contact <contact-index> [reason]")
            try await printKeyValues(api.post("api/contacts/blacklist/contact/\(encode(contactIndex(args[0])))", body: [
                "reason": args.dropFirst().joined(separator: " ")
            ].compactValues()))
        case "blacklist-remove":
            try require(args, 1, "blacklist-remove <account-id>")
            try await printKeyValues(api.delete("api/contacts/blacklist/\(encode(args[0]))"))
        case "send", "send-file":
            try require(args, 2, "send-file <file-path> <target-account-id>")
            try await printKeyValues(api.post("api/send", body: ["filePath": args[0], "targetAccountId": args[1]]))
        case "tasks":
            try await printTable(api.get("api/send/tasks"), preferredColumns: ["taskId", "transferId", "fileName", "status", "progress", "peerAccountId", "createdAt", "updatedAt"])
        case "task":
            try require(args, 1, "task <task-id-or-transfer-id>")
            try await printKeyValues(api.get("api/send/tasks/\(encode(args[0]))"))
        case "cancel", "task-cancel":
            try require(args, 1, "\(command) <task-id-or-transfer-id>")
            try await printKeyValues(api.post("api/send/tasks/\(encode(args[0]))/cancel"))
        case "retransmit":
            try require(args, 1, "retransmit <task-id-or-transfer-id>")
            try await printKeyValues(api.post("api/receive/retransmit", body: ["taskIdOrTransferId": args[0]]))
        case "retransmit-requests":
            try await printTable(api.get("api/receive/retransmit-requests"), preferredColumns: ["transferId", "fileName", "senderAccountId", "status", "createdAt"])
        case "retransmit-accept":
            try require(args, 1, "retransmit-accept <transfer-id>")
            try await printKeyValues(api.post("api/receive/retransmit-accept", body: ["transferId": args[0]]))
        case "retransmit-reject":
            try require(args, 1, "retransmit-reject <transfer-id>")
            try await printKeyValues(api.post("api/receive/retransmit-reject", body: ["transferId": args[0]]))
        case "open-received", "open-receive", "open-file":
            try require(args, 1, "\(command) <task-id-or-transfer-id-or-file-name>")
            try await printKeyValues(api.post("api/receive/open-received", body: ["target": args.joined(separator: " ")]))
        case "incoming":
            try await printTable(api.get("api/receive/incoming"), preferredColumns: ["transferId", "fileName", "senderAccountId", "fileSize", "status", "createdAt"])
        case "accept":
            try require(args, 1, "accept <transfer-id>")
            try await printKeyValues(api.post("api/receive/accept", body: ["transferId": args[0]]))
        case "reject":
            try require(args, 1, "reject <transfer-id>")
            try await printKeyValues(api.post("api/receive/reject", body: ["transferId": args[0]]))
        case "messages":
            try await printTable(api.get("api/messages"), preferredColumns: ["peerAccountId", "alias", "unreadCount", "lastDirection", "lastStatus", "lastMessageTime"])
        case "conversation", "message":
            try require(args, 1, "\(command) <peer-account-id>")
            try await printTable(api.get("api/messages/\(encode(args[0]))"), preferredColumns: ["messageId", "direction", "status", "createdAt", "body"])
        case "message-send":
            try require(args, 1, "message-send <target-account-id-or-contact-N> [text]")
            let text = args.count > 1
                ? args.dropFirst().joined(separator: " ")
                : readMultilineUntilDot(prompt: "message")
            try await printKeyValues(api.post("api/messages/send", body: [
                "targetAccountId": args[0],
                "text": text
            ]))
        case "offline-file-encrypt", "fst-file-encrypt":
            try await offlineFileEncrypt(args)
        case "offline-file-decrypt", "fst-file-decrypt":
            try require(args, 1, "offline-file-decrypt <fst2-path> [output-dir]")
            var body: [String: Any] = ["fst2Path": args[0]]
            if args.count > 1 {
                body["outputDir"] = args[1]
            }
            try await printKeyValues(api.post("api/offline/files/decrypt", body: body))
        case "offline-text-encrypt", "fst-text-encrypt":
            try await offlineTextEncrypt(args)
        case "offline-text-decrypt", "fst-text-decrypt":
            let payload = args.isEmpty
                ? readMultilineUntilDot(prompt: "payload")
                : args.joined(separator: " ")
            guard !payload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                throw ValidationError("Usage: offline-text-decrypt <payload>")
            }
            try await printKeyValues(api.post("api/offline/text/decrypt", body: [
                "payload": payload
            ]))
        default:
            print("Unknown command: \(command)")
            print("Type 'help' for available commands.")
        }
        return true
    }

    private mutating func setBaseURL(_ args: [String]) throws {
        try require(args, 1, "base-url <http-url>")
        guard let url = URL(string: args[0]), url.scheme?.hasPrefix("http") == true else {
            throw ValidationError("Invalid base URL.")
        }
        api.baseURL = url
        print("HTTP service: \(api.baseURL.absoluteString)")
    }

    private func raw(_ args: [String]) async throws {
        try require(args, 2, "raw <get|post|put|delete> <api/path> [json-body]")
        let method = args[0].lowercased()
        let path = args[1].trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let body = try parseJSONBody(Array(args.dropFirst(2)))
        switch method {
        case "get":
            try await printJSON(api.get(path))
        case "post":
            try await printJSON(api.post(path, body: body))
        case "put":
            try await printJSON(api.put(path, body: body))
        case "delete":
            try await printJSON(api.delete(path))
        default:
            throw ValidationError("Usage: raw <get|post|put|delete> <api/path> [json-body]")
        }
    }

    private func language(_ args: [String]) async throws {
        guard let requested = args.first else {
            try await printKeyValues(api.get("api/system/language"))
            return
        }
        try await printKeyValues(api.post("api/system/language", body: ["language": requested]))
    }

    private func connect(_ args: [String]) async throws {
        let host = args.first ?? "127.0.0.1"
        let portText = args.count > 1 ? args[1] : "20202"
        guard let port = Int(portText), port > 0 else {
            throw ValidationError("Port must be a positive integer.")
        }
        try await printKeyValues(api.post("api/system/connect", body: ["host": host, "port": port]))
    }

    private func addContact(_ args: [String]) async throws {
        try require(args, 1, "contact-add <account-id> [alias]")
        try await printKeyValues(api.post("api/contacts", body: [
            "accountId": args[0],
            "alias": args.dropFirst().joined(separator: " ")
        ].compactValues()))
    }

    private func addContactWithPublicKey(_ args: [String]) async throws {
        try require(args, 1, "contact-add-public-key <public-key-text-or-path> [alias]")
        let publicKey = args[0]
        var body: [String: Any] = ["alias": args.dropFirst().joined(separator: " ")].compactValues()
        if FileManager.default.fileExists(atPath: publicKey) {
            body["publicKeyPath"] = publicKey
        } else {
            body["publicKey"] = publicKey
        }
        try await printKeyValues(api.post("api/contacts", body: body))
    }

    private func updateContact(_ args: [String]) async throws {
        try require(args, 2, "contact-update <contact-index> <alias>")
        try await printKeyValues(api.put("api/contacts/\(encode(contactIndex(args[0])))", body: [
            "alias": args.dropFirst().joined(separator: " ")
        ]))
    }

    private func updateContactPublicKey(_ args: [String]) async throws {
        try require(args, 2, "contact-update-public-key <contact-index> <public-key-text-or-path>")
        let value = args.dropFirst().joined(separator: " ")
        var body: [String: Any] = [:]
        if FileManager.default.fileExists(atPath: value) {
            body["publicKeyPath"] = value
        } else {
            body["publicKey"] = value
        }
        try await printKeyValues(api.put("api/contacts/\(encode(contactIndex(args[0])))", body: body))
    }

    private func printContact(indexText: String) async throws {
        let wanted = Int(contactIndex(indexText))
        let data = try await api.get("api/contacts")
        guard let contacts = try JSONSerialization.jsonObject(with: data) as? [[String: Any]],
              let contact = contacts.first(where: { ($0["contactIndex"] as? Int) == wanted }) else {
            throw ValidationError("Contact not found: \(indexText)")
        }
        try printKeyValues(JSONSerialization.data(withJSONObject: contact, options: []))
    }

    private func offlineFileEncrypt(_ args: [String]) async throws {
        try require(args, 2, "offline-file-encrypt <file-path> <receiver-key-or-path-or-contact-index> [output-dir]")
        var body: [String: Any] = ["filePath": args[0]]
        if args.count > 2 {
            body["outputDir"] = args[2]
        }
        addReceiver(args[1], to: &body)
        try await printKeyValues(api.post("api/offline/files/encrypt", body: body))
    }

    private func offlineTextEncrypt(_ args: [String]) async throws {
        try require(args, 1, "offline-text-encrypt <receiver-key-or-path-or-contact-index> [text]")
        let text = args.count > 1
            ? args.dropFirst().joined(separator: " ")
            : readMultilineUntilDot(prompt: "text")
        var body: [String: Any] = ["text": text]
        addReceiver(args[0], to: &body)
        try await printKeyValues(api.post("api/offline/text/encrypt", body: body))
    }

    private func addReceiver(_ value: String, to body: inout [String: Any]) {
        if Int(value) != nil {
            body["contactIndex"] = contactIndex(value)
        } else if value.hasPrefix("contact-"), Int(contactIndex(value)) != nil {
            body["contactIndex"] = contactIndex(value)
        } else if FileManager.default.fileExists(atPath: value) {
            body["receiverPublicKeyPath"] = value
        } else {
            body["receiverPublicKey"] = value
        }
    }

    private func printJSON(_ data: Data) throws {
        guard !data.isEmpty else {
            print("OK")
            return
        }
        if let object = try? JSONSerialization.jsonObject(with: data),
           JSONSerialization.isValidJSONObject(object),
           let pretty = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]),
           let text = String(data: pretty, encoding: .utf8) {
            print(text)
        } else {
            print(String(data: data, encoding: .utf8) ?? "")
        }
    }

    private func printKeyValues(_ data: Data) throws {
        guard !data.isEmpty else {
            print("OK")
            return
        }
        guard let object = try? JSONSerialization.jsonObject(with: data) else {
            try printJSON(data)
            return
        }
        if let dictionary = object as? [String: Any] {
            printDictionary(dictionary)
        } else if let array = object as? [[String: Any]] {
            printTable(array, preferredColumns: [])
        } else {
            print(stringValue(object))
        }
    }

    private func printTable(_ data: Data, preferredColumns: [String]) throws {
        guard !data.isEmpty else {
            print("OK")
            return
        }
        guard let object = try? JSONSerialization.jsonObject(with: data) else {
            try printJSON(data)
            return
        }
        if let rows = object as? [[String: Any]] {
            printTable(rows, preferredColumns: preferredColumns)
        } else if let dictionary = object as? [String: Any] {
            printDictionary(dictionary)
        } else {
            print(stringValue(object))
        }
    }

    private func printDictionary(_ dictionary: [String: Any]) {
        guard !dictionary.isEmpty else {
            print("(empty)")
            return
        }
        let keys = dictionary.keys.sorted()
        let keyWidth = min(max(keys.map(\.count).max() ?? 0, 4), 28)
        for key in keys {
            let paddedKey = key.padding(toLength: keyWidth, withPad: " ", startingAt: 0)
            print("\(paddedKey) : \(stringValue(dictionary[key] ?? NSNull()))")
        }
    }

    private func printTable(_ rows: [[String: Any]], preferredColumns: [String]) {
        guard !rows.isEmpty else {
            print("(none)")
            return
        }

        let rowKeys = Set(rows.flatMap { $0.keys })
        let preferred = preferredColumns.filter { rowKeys.contains($0) }
        let remaining = rowKeys.subtracting(preferred).sorted()
        let columns = preferred + remaining
        guard !columns.isEmpty else {
            print("(empty rows)")
            return
        }

        let values = rows.map { row in
            columns.map { truncate(stringValue(row[$0] ?? ""), limit: 42) }
        }
        let widths = columns.enumerated().map { index, column in
            min(max(column.count, values.map { $0[index].count }.max() ?? 0), 42)
        }
        let header = columns.enumerated()
            .map { index, column in truncate(column, limit: widths[index]).padding(toLength: widths[index], withPad: " ", startingAt: 0) }
            .joined(separator: "  ")
        let separator = widths.map { String(repeating: "-", count: $0) }.joined(separator: "  ")
        print(header)
        print(separator)
        for row in values {
            print(row.enumerated()
                .map { index, value in value.padding(toLength: widths[index], withPad: " ", startingAt: 0) }
                .joined(separator: "  "))
        }
    }

    private func printHelp() {
        print("""
        Commands:
          raw <get|post|put|delete> <api/path> [json-body]
          base-url <http-url>
          status | startup-status | key-info | generate-key | delete-key | skip-key-setup
          import-private-key <private-key-text-or-path>
          import-private-key-file <path> | import-private-key-paste
          export-public-key | export-private-key
          public-key | public-key-fingerprint [public-key] | account-id [public-key]
          language [english|chinese]
          connection-status | connect [host] [port] | disconnect
          contacts | contact-add <account-id> [alias] | contact-show <contact-index>
          contact-add-public-key <public-key-text-or-path> [alias]
          contact-update <contact-index> <alias>
          contact-update-public-key <contact-index> <public-key-text-or-path>
          contact-delete <contact-index> | contact-remove <contact-index>
          contact-search/search-user <account-id>
          contact-search-add/search-user-add <account-id> [alias]
          blacklist | blacklist-add <account-id> [reason]
          blacklist-add-contact <contact-index> [reason] | blacklist-remove <account-id>
          send/send-file <file-path> <target-account-id>
          tasks | task <task-id-or-transfer-id> | cancel/task-cancel <task-id-or-transfer-id>
          retransmit <task-id-or-transfer-id> | retransmit-requests
          retransmit-accept <transfer-id> | retransmit-reject <transfer-id>
          open-received/open-receive/open-file <task-id-or-transfer-id-or-file-name>
          incoming | accept <transfer-id> | reject <transfer-id>
          messages | message/conversation <peer-account-id> | message-send <target-account-id> <text>
          offline-file-encrypt/fst-file-encrypt <file-path> <receiver-key-or-path-or-contact-index> [output-dir]
          offline-file-decrypt/fst-file-decrypt <fst2-path> [output-dir]
          offline-text-encrypt/fst-text-encrypt <receiver-key-or-path-or-contact-index> <text>
          offline-text-decrypt/fst-text-decrypt <payload>
          help | exit

        Direct mode is intentionally not available.
        """)
    }
}

struct ValidationError: LocalizedError {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var errorDescription: String? { message }
}

func require(_ args: [String], _ count: Int, _ usage: String) throws {
    if args.count < count {
        throw ValidationError("Usage: \(usage)")
    }
}

func tokenize(_ input: String) -> [String] {
    var result: [String] = []
    var current = ""
    var quote: Character?
    var escaping = false

    for char in input {
        if escaping {
            current.append(char)
            escaping = false
            continue
        }
        if char == "\\" {
            escaping = true
            continue
        }
        if let activeQuote = quote {
            if char == activeQuote {
                quote = nil
            } else {
                current.append(char)
            }
            continue
        }
        if char == "\"" || char == "'" {
            quote = char
            continue
        }
        if char.isWhitespace {
            if !current.isEmpty {
                result.append(current)
                current = ""
            }
            continue
        }
        current.append(char)
    }

    if !current.isEmpty {
        result.append(current)
    }
    return result
}

func encode(_ value: String) -> String {
    value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value
}

func contactIndex(_ value: String) -> String {
    value.hasPrefix("contact-") ? String(value.dropFirst("contact-".count)) : value
}

func readMultilineUntilDot(prompt: String) -> String {
    print("Paste \(prompt). End with a single '.' line.")
    var lines: [String] = []
    while true {
        print("\(prompt)> ", terminator: "")
        guard let line = readLine() else {
            break
        }
        if line.trimmingCharacters(in: .whitespacesAndNewlines) == "." {
            break
        }
        lines.append(line)
    }
    return lines.joined(separator: "\n")
}

func parseJSONBody(_ args: [String]) throws -> [String: Any] {
    guard !args.isEmpty else {
        return [:]
    }
    let text = args.joined(separator: " ")
    guard let data = text.data(using: .utf8),
          let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        throw ValidationError("JSON body must be an object, for example: {\"host\":\"127.0.0.1\"}")
    }
    return object
}

func stringValue(_ value: Any) -> String {
    switch value {
    case is NSNull:
        return ""
    case let bool as Bool:
        return bool ? "true" : "false"
    case let number as NSNumber:
        return number.stringValue
    case let string as String:
        return string
    case let dictionary as [String: Any]:
        return compactJSONString(dictionary)
    case let array as [Any]:
        return compactJSONString(array)
    default:
        return String(describing: value)
    }
}

func compactJSONString(_ value: Any) -> String {
    guard JSONSerialization.isValidJSONObject(value),
          let data = try? JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]),
          let text = String(data: data, encoding: .utf8) else {
        return String(describing: value)
    }
    return text
}

func truncate(_ value: String, limit: Int) -> String {
    guard value.count > limit, limit > 1 else {
        return value
    }
    return String(value.prefix(limit - 1)) + "..."
}

extension Dictionary where Key == String, Value == Any {
    func compactValues() -> [String: Any] {
        var compacted: [String: Any] = [:]
        for (key, value) in self {
            if let text = value as? String {
                let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    compacted[key] = text
                }
            } else if !(value is OptionalProtocol) {
                compacted[key] = value
            }
        }
        return compacted
    }
}

protocol OptionalProtocol {}
extension Optional: OptionalProtocol {}
