import SwiftUI
import UniformTypeIdentifiers

struct Contact: Codable, Identifiable {
    var id = UUID()
    var name: String
    var pem: String
}
@MainActor final class AppStore: ObservableObject {
    @Published var contacts: [Contact] = []
    @Published var publicPEM = ""
    @Published var error: String?
    @Published var busy = false
    @Published var progress = 0.0
    @Published var output: URL?
    @Published var incoming: URL?
    private var worker: Task<Void, Never>?
    private let directory: URL
    init() {
        directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("FileSecurity", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
            var excluded = directory
            var values = URLResourceValues(); values.isExcludedFromBackup = true; try excluded.setResourceValues(values)
            if let data = try? Data(contentsOf: directory.appendingPathComponent("contacts.json")) {
                contacts = try JSONDecoder().decode([Contact].self, from: data)
            }
            if KeyVault.exists { publicPEM = (try? String(contentsOf: directory.appendingPathComponent("public.txt"), encoding: .utf8)) ?? "" }
            clearTemporaryFiles()
        } catch { self.error = error.localizedDescription }
    }
    var temporaryDirectory: URL { FileManager.default.temporaryDirectory.appendingPathComponent("FST", isDirectory: true) }
    func clearTemporaryFiles() {
        guard !busy else { return }
        try? FileManager.default.removeItem(at: temporaryDirectory); output = nil
    }
    func saveContacts(_ updated: [Contact]) throws {
        try JSONEncoder().encode(updated).write(to: directory.appendingPathComponent("contacts.json"), options: [.atomic, .completeFileProtection])
        contacts = updated
    }
    func addContact(name: String, payload: String) throws {
        let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        try require(!name.isEmpty && name.count <= 80, "Enter a name up to 80 characters")
        let pem = try PublicKey.normalize(payload)
        try require(!contacts.contains { $0.pem == pem }, "This public key is already in Contacts")
        try saveContacts(contacts + [Contact(name: name, pem: pem)])
    }
    func generateKey() {
        guard !busy else { return }; busy = true
        Task {
            do {
                let pem = try await Task.detached(priority: .userInitiated) { try KeyVault.generate() }.value
                try Data(pem.utf8).write(to: directory.appendingPathComponent("public.txt"), options: [.atomic, .completeFileProtection])
                publicPEM = pem
            } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
    func recoverPublicKey() {
        guard !busy else { return }; busy = true
        Task {
            do {
                let pem = try await Task.detached { try PublicKey.export(SecKeyCopyPublicKey(KeyVault.privateKey())!) }.value
                try Data(pem.utf8).write(to: directory.appendingPathComponent("public.txt"), options: [.atomic, .completeFileProtection])
                publicPEM = pem
            } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
    func deleteKey() {
        guard !busy else { return }; busy = true
        Task {
            do {
                try await Task.detached { try KeyVault.remove() }.value
                publicPEM = ""; try? FileManager.default.removeItem(at: directory.appendingPathComponent("public.txt"))
            } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
    func transformText(_ text: String, recipient: String?, recipients: [String]? = nil, completion: @escaping (String) -> Void) {
        guard !busy else { return }; busy = true
        Task {
            do {
                let result = try await Task.detached(priority: .userInitiated) {
                    if recipients != nil || recipient != nil { try require(KeyVault.exists, "Create a local key first") }
                    if let recipients { return try MultiEnvelope.encryptText(text, recipients: recipients) }
                    if let recipient { return try FSTCrypto.encryptText(text, recipient: recipient) }
                    return try FSTCrypto.decryptText(text, privateKey: KeyVault.privateKey())
                }.value
                completion(result)
            } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
    func processFile(_ url: URL, recipient: String?, recipients: [String]? = nil, onSuccess: @escaping () -> Void = {}) {
        guard !busy else { return }
        clearTemporaryFiles(); busy = true; progress = 0
        let temp = temporaryDirectory
        worker = Task {
            let work = Task.detached(priority: .userInitiated) { () throws -> URL in
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                try FileManager.default.createDirectory(at: temp, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.complete])
                let staging = temp.appendingPathComponent(UUID().uuidString)
                if recipients != nil || recipient != nil { try require(KeyVault.exists, "Create a local key first") }
                let key = recipient == nil && recipients == nil ? try KeyVault.privateKey() : nil
                let coordinator = NSFileCoordinator()
                var coordinationError: NSError?
                var outcome: Result<String, Error>?
                coordinator.coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinated in
                    outcome = Result {
                        try FSTCrypto.processFile(input: coordinated, output: staging, recipient: recipient, privateKey: key, recipients: recipients) { value in
                            Task { @MainActor in self.progress = value }
                        }
                    }
                }
                if let coordinationError { throw coordinationError }
                guard let outcome else { throw FSTError.invalid("Unable to open input file") }
                let name = try outcome.get()
                try Task.checkCancellation()
                let result = temp.appendingPathComponent(name)
                if result != staging { try FileManager.default.moveItem(at: staging, to: result) }
                return result
            }
            do {
                let result = try await withTaskCancellationHandler { try await work.value } onCancel: { work.cancel() }
                output = result
                onSuccess()
            } catch {
                try? FileManager.default.removeItem(at: temp)
                if !(error is CancellationError) { self.error = error.localizedDescription }
            }
            busy = false; worker = nil
        }
    }
    func cancel() { worker?.cancel() }
}
