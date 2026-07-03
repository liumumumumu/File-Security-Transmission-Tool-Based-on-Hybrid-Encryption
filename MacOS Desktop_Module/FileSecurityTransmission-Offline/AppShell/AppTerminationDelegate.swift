import AppKit
import Foundation

final class AppTerminationDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool {
        false
    }

    func applicationWillTerminate(_ notification: Notification) {
        requestBackendShutdown()
    }

    private func requestBackendShutdown() {
        guard let url = URL(string: "http://127.0.0.1:20201/api/system/shutdown") else {
            return
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 1.5

        let semaphore = DispatchSemaphore(value: 0)
        let task = URLSession.shared.dataTask(with: request) { _, _, _ in
            semaphore.signal()
        }
        task.resume()

        _ = semaphore.wait(timeout: .now() + 2.0)
    }
}
