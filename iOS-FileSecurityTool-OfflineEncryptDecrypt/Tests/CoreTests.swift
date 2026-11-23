import XCTest
import Security
@testable import FileSecurityCore

final class CoreTests: XCTestCase {
    func vectors() throws -> [String: String] {
        try JSONDecoder().decode([String: String].self, from: Data(contentsOf: Bundle.module.url(forResource: "Vectors", withExtension: "json")!))
    }
    func privateKey() throws -> SecKey {
        let der = Data(base64Encoded: try vectors()["PRIVATE_PKCS8_BASE64"]!)!
        // Fixed public test fixture only: PKCS#8's version + algorithm precede the PKCS#1 OCTET STRING.
        func tlv(_ data: Data, _ offset: inout Int) -> (UInt8, Data) {
            let tag = data[offset]; offset += 1
            var length = Int(data[offset]); offset += 1
            if length > 127 {
                let count = length & 127; length = 0
                for _ in 0..<count { length = length << 8 | Int(data[offset]); offset += 1 }
            }
            let body = data.subdata(in: offset..<offset + length); offset += length
            return (tag, body)
        }
        var offset = 0
        let body = tlv(der, &offset).1; offset = 0
        _ = tlv(body, &offset); _ = tlv(body, &offset)
        let raw = tlv(body, &offset).1
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(raw as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPrivate] as CFDictionary, &error) else { throw error!.takeRetainedValue() }
        return key
    }
    func temporary() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }; return url
    }
    func testCanonicalAndHostileCBOR() throws {
        XCTAssertEqual(CBOR.encode(["b": .number(2), "a": .number(1)]), Data([0xa2,0x61,0x61,1,0x61,0x62,2]))
        for data in [Data([0xa1]), Data([0xa0,0]), Data([0xa1,0x61,0x61,0x5b,255,255,255,255,255,255,255,255]), Data([0xa2,0x61,0x61,1,0x61,0x61,2])] {
            XCTAssertThrowsError(try CBOR.decode(data))
        }
    }
    func testJavaAndAndroidVectors() throws {
        let v = try vectors(), key = try privateKey(), dir = try temporary()
        XCTAssertEqual(try FSTCrypto.decryptText(v["JAVA_FST_TEXT1"]!, privateKey: key), "Java to Android FST-TEXT1 互操作")
        XCTAssertEqual(try FSTCrypto.decryptText(v["ANDROID_FST_TEXT1"]!, privateKey: key), "Android to Java FST-TEXT1 互操作")
        for (name, expected) in [("JAVA_FST2_BASE64", "Java to Android FST2 interoperability vector\\n第二行"), ("ANDROID_FST2_BASE64", "Android to Java FST2 interoperability vector")] {
            let input = dir.appendingPathComponent(name), output = dir.appendingPathComponent(UUID().uuidString)
            try Data(base64Encoded: v[name]!)!.write(to: input)
            _ = try FSTCrypto.processFile(input: input, output: output, recipient: nil, privateKey: key)
            XCTAssertEqual(try String(contentsOf: output, encoding: .utf8), expected)
        }
    }
    func testPublicKeyAndTextRoundTrip() throws {
        let key = try privateKey(), pem = try PublicKey.export(SecKeyCopyPublicKey(key)!)
        XCTAssertEqual(try PublicKey.normalize(pem), pem)
        XCTAssertEqual(try PublicKey.normalize(PublicKey.artifact(pem)), pem)
        let encrypted = try FSTCrypto.encryptText("你好 iPhone 🔒", recipient: pem)
        XCTAssertEqual(try FSTCrypto.decryptText(encrypted, privateKey: key), "你好 iPhone 🔒")
        XCTAssertThrowsError(try FSTCrypto.encryptText(String(repeating: "中", count: 6000), recipient: pem))
        XCTAssertThrowsError(try FSTCrypto.decryptText(encrypted + "AA", privateKey: key))
    }
    func testFileBoundariesTamperingAndCleanup() throws {
        let key = try privateKey(), pem = try PublicKey.export(SecKeyCopyPublicKey(key)!), dir = try temporary()
        for size in [0, 1, 1_048_576, 1_048_577] {
            let input = dir.appendingPathComponent("original"), cipher = dir.appendingPathComponent("encrypted"), output = dir.appendingPathComponent("decrypted")
            let data = Data(repeating: 42, count: size); try data.write(to: input)
            _ = try FSTCrypto.processFile(input: input, output: cipher, recipient: pem, privateKey: nil)
            XCTAssertEqual(try FSTCrypto.processFile(input: cipher, output: output, recipient: nil, privateKey: key), "original")
            XCTAssertEqual(try Data(contentsOf: output), data)
            var damaged = try Data(contentsOf: cipher); damaged[damaged.count - 1] ^= 1; try damaged.write(to: cipher)
            XCTAssertThrowsError(try FSTCrypto.processFile(input: cipher, output: output, recipient: nil, privateKey: key))
            XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
        }
        XCTAssertEqual(FSTCrypto.safeName("../../escape.txt"), "escape.txt")
        XCTAssertEqual(FSTCrypto.safeName(".."), "decrypted-file")
    }
    func testTruncationTrailingDataAndWrongKey() throws {
        let key = try privateKey(), dir = try temporary(), v = try vectors()
        let valid = Data(base64Encoded: v["JAVA_FST2_BASE64"]!)!
        let input = dir.appendingPathComponent("input"), output = dir.appendingPathComponent("output")
        for broken in [Data(valid.prefix(7)), Data(valid.dropLast()), valid + Data([0]), Data([0,0,0,0]) + valid.dropFirst(4)] {
            try broken.write(to: input)
            XCTAssertThrowsError(try FSTCrypto.processFile(input: input, output: output, recipient: nil, privateKey: key))
            XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
        }
        var error: Unmanaged<CFError>?
        let wrong = SecKeyCreateRandomKey([kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeySizeInBits: 2048] as CFDictionary, &error)!
        XCTAssertThrowsError(try FSTCrypto.decryptText(v["JAVA_FST_TEXT1"]!, privateKey: wrong))
    }
    func testCancelledFileTaskCleansOutput() async throws {
        let key = try privateKey(), pem = try PublicKey.export(SecKeyCopyPublicKey(key)!), dir = try temporary()
        let input = dir.appendingPathComponent("input"), output = dir.appendingPathComponent("output")
        try Data(repeating: 1, count: 2_097_152).write(to: input)
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try FSTCrypto.processFile(input: input, output: output, recipient: pem, privateKey: nil)
        }
        do { _ = try await task.value; XCTFail("Expected cancellation") } catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: output.path))
    }

}
