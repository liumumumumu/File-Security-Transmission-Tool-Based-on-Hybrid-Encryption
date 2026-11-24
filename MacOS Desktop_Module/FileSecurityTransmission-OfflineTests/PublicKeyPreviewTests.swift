import Foundation
import Testing
@testable import FileSecurityTransmission

struct PublicKeyPreviewTests {
    @MainActor
    @Test func previewLoadsFingerprintWhenKeyStatusContainsOnlyAvailability() async throws {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [PublicKeyPreviewProtocol.self]
        let session = URLSession(configuration: configuration)
        defer { session.invalidateAndCancel() }
        let viewModel = AppViewModel(apiClient: FSTAPIClient(session: session))

        let preview = try await viewModel.publicKeyPreview()

        #expect(preview.fingerprint == "test-public-key-fingerprint")
        #expect(preview.artifact.pngPath == "/tmp/public-key.png")
        #expect(!viewModel.operationInProgress)
    }
}

private final class PublicKeyPreviewProtocol: URLProtocol, @unchecked Sendable {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let body: String
        switch (request.httpMethod, request.url?.path) {
        case ("GET", "/api/system/key"):
            // The real status endpoint does not include an account ID or fingerprint.
            body = #"{"hasPrivateKey":true,"hasPublicKey":true}"#
        case ("POST", "/api/system/key/export-public"):
            body = #"{"publicKey":"test-public-key","pngPath":"/tmp/public-key.png","qrText":"test-qr"}"#
        case ("POST", "/api/system/key/fingerprint"):
            let data = request.httpBody ?? request.httpBodyStream.map { stream in
                stream.open()
                defer { stream.close() }
                var data = Data()
                var buffer = [UInt8](repeating: 0, count: 1024)
                while stream.hasBytesAvailable {
                    let count = stream.read(&buffer, maxLength: buffer.count)
                    guard count > 0 else { break }
                    data.append(buffer, count: count)
                }
                return data
            }
            guard let data,
                  let payload = try? JSONSerialization.jsonObject(with: data) as? [String: String],
                  payload["publicKey"] == "test-public-key" else {
                client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
                return
            }
            body = #"{"fingerprint":"test-public-key-fingerprint"}"#
        default:
            client?.urlProtocol(self, didFailWithError: URLError(.unsupportedURL))
            return
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(body.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
