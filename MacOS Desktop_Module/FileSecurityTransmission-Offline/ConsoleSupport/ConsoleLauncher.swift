import AppKit
import Foundation

enum ConsoleLauncher {
    enum LaunchError: LocalizedError {
        case missingExecutable(String)
        case unableToWriteCommand(String)

        var errorDescription: String? {
            switch self {
            case .missingExecutable(let name):
                "\(name) is not bundled yet."
            case .unableToWriteCommand(let message):
                "Unable to open console: \(message)"
            }
        }
    }

    static func openConsole(serviceAvailable: Bool) throws {
        let commandPath = commandFileURL(serviceAvailable: serviceAvailable)
        do {
            let executable = try executableURL(serviceAvailable: serviceAvailable)
            try FileManager.default.createDirectory(
                at: commandPath.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try commandScript(executable: executable).write(
                to: commandPath,
                atomically: true,
                encoding: .utf8
            )
            try FileManager.default.setAttributes([.posixPermissions: 0o755], ofItemAtPath: commandPath.path)
            NSWorkspace.shared.open(
                [commandPath],
                withApplicationAt: URL(fileURLWithPath: "/System/Applications/Utilities/Terminal.app"),
                configuration: NSWorkspace.OpenConfiguration()
            )
        } catch {
            NSSound.beep()
            if let launchError = error as? LaunchError {
                throw launchError
            }
            throw LaunchError.unableToWriteCommand(error.localizedDescription)
        }
    }

    private static func commandFileURL(serviceAvailable: Bool) -> URL {
        let fileName = serviceAvailable ? "open-fst-console.command" : "open-fst-legacy-console.command"
        return FileManager.default
            .homeDirectoryForCurrentUser
            .appending(path: ".file-security-transmission")
            .appending(path: fileName)
    }

    private static func commandScript(executable: URL) -> String {
        return """
        #!/bin/zsh
        exec "\(executable.path)"
        """
    }

    private static func executableURL(serviceAvailable: Bool) throws -> URL {
        let name = serviceAvailable ? "fst-console" : "FileSecurityTransmission"
        let executable = bundledExecutable(named: name)
        guard FileManager.default.isExecutableFile(atPath: executable.path) else {
            throw LaunchError.missingExecutable(name)
        }
        return executable
    }

    private static func bundledExecutable(named name: String) -> URL {
        let bundleExecutableDirectory = Bundle.main.bundleURL
            .appending(path: "Contents")
            .appending(path: "MacOS")
        let bundled = bundleExecutableDirectory.appending(path: name)
        if FileManager.default.isExecutableFile(atPath: bundled.path) {
            return bundled
        }
        return Bundle.main.executableURL?.deletingLastPathComponent().appending(path: name) ?? bundled
    }
}
