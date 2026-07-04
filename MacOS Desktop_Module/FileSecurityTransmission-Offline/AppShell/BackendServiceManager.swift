import Foundation

final class BackendServiceManager: @unchecked Sendable {
    static let shared = BackendServiceManager()

    private let lock = NSLock()
    private var process: Process?
    private let baseURL = URL(string: "http://127.0.0.1:20201")!

    private init() {}

    func ensureRunning() async throws {
        if await isReady() { return }
        try startIfNeeded()

        let deadline = Date().addingTimeInterval(45)
        while Date() < deadline {
            if await isReady() { return }
            try await Task.sleep(for: .milliseconds(500))
        }
        throw BackendServiceError.startupTimedOut
    }

    func shutdown() {
        let semaphore = DispatchSemaphore(value: 0)
        var request = URLRequest(url: baseURL.appending(path: "/api/system/shutdown"))
        request.httpMethod = "POST"
        request.timeoutInterval = 1.5
        URLSession.shared.dataTask(with: request) { _, _, _ in semaphore.signal() }.resume()
        _ = semaphore.wait(timeout: .now() + 2)

        lock.lock()
        let child = process
        process = nil
        lock.unlock()
        if let child, child.isRunning {
            child.terminate()
        }
    }

    private func startIfNeeded() throws {
        lock.lock()
        defer { lock.unlock() }
        if let process, process.isRunning { return }

        let backend = try backendLayout()
        let dataDirectory = FileManager.default.homeDirectoryForCurrentUser
            .appending(path: ".file-security-transmission", directoryHint: .isDirectory)
        let downloadsDirectory = FileManager.default.homeDirectoryForCurrentUser
            .appending(path: "Downloads/FileSecurityTransmission", directoryHint: .isDirectory)
        let logsDirectory = dataDirectory.appending(path: "logs", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: dataDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: downloadsDirectory, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: logsDirectory, withIntermediateDirectories: true)

        let child = Process()
        child.executableURL = backend.java
        child.currentDirectoryURL = dataDirectory
        child.arguments = [
            "-Dfile.encoding=UTF-8",
            "-jar", backend.jar.path,
            "--app.role=client",
            "--spring.profiles.active=client",
            "--server.tcp.enabled=false",
            "--server.address=127.0.0.1",
            "--server.port=20201",
            "--app.local-storage.transfer-history-path=\(dataDirectory.appending(path: "transfer-history.json").path)",
            "--app.local-storage.sqlite-path=\(dataDirectory.appending(path: "local-data.db").path)",
            "--app.local-storage.device-id-path=\(dataDirectory.appending(path: "device-id").path)",
            "--transfer.receive-dir=\(downloadsDirectory.path)"
        ]

        let logURL = logsDirectory.appending(path: "tcp-client.log")
        if !FileManager.default.fileExists(atPath: logURL.path) {
            FileManager.default.createFile(atPath: logURL.path, contents: nil)
        }
        let log = try FileHandle(forWritingTo: logURL)
        try log.seekToEnd()
        child.standardOutput = log
        child.standardError = log
        child.terminationHandler = { [weak self] finished in
            try? log.close()
            self?.lock.lock()
            if self?.process === finished { self?.process = nil }
            self?.lock.unlock()
        }
        try child.run()
        process = child
    }

    private func backendLayout() throws -> (java: URL, jar: URL) {
        guard let resources = Bundle.main.resourceURL else {
            throw BackendServiceError.missingResources
        }
        let root = resources.appending(path: "backend", directoryHint: .isDirectory)
        let java = root.appending(path: "runtime/bin/java")
        let jar = root.appending(path: "tcp-client.jar")
        guard FileManager.default.isExecutableFile(atPath: java.path) else {
            throw BackendServiceError.missingJava(java.path)
        }
        guard FileManager.default.fileExists(atPath: jar.path) else {
            throw BackendServiceError.missingJar(jar.path)
        }
        return (java, jar)
    }

    private func isReady() async -> Bool {
        var request = URLRequest(url: baseURL.appending(path: "/api/system/status"))
        request.timeoutInterval = 0.7
        do {
            let (_, response) = try await URLSession.shared.data(for: request)
            return (response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
        } catch {
            return false
        }
    }
}

enum BackendServiceError: LocalizedError {
    case missingResources
    case missingJava(String)
    case missingJar(String)
    case startupTimedOut

    var errorDescription: String? {
        switch self {
        case .missingResources: "The application backend resources are missing."
        case .missingJava(let path): "The bundled Java runtime is missing or not executable: \(path)"
        case .missingJar(let path): "The bundled Java backend is missing: \(path)"
        case .startupTimedOut: "The local Java service did not become ready within 45 seconds."
        }
    }
}
