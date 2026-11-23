import Foundation
import Security
import CryptoKit

public enum PublicKey {
    // Security exports PKCS#1; Android/Java exchange SubjectPublicKeyInfo.
    static let algorithm = Data([0x30,0x0d,0x06,0x09,0x2a,0x86,0x48,0x86,0xf7,0x0d,0x01,0x01,0x01,0x05,0x00])
    static func der(_ tag: UInt8, _ body: Data) -> Data {
        let n = body.count
        let length: Data = n < 128 ? Data([UInt8(n)]) : n < 256 ? Data([0x81,UInt8(n)]) : Data([0x82,UInt8(n >> 8),UInt8(n & 255)])
        return Data([tag]) + length + body
    }
    public static func export(_ key: SecKey) throws -> String {
        var error: Unmanaged<CFError>?
        guard let raw = SecKeyCopyExternalRepresentation(key, &error) as Data? else { throw error!.takeRetainedValue() }
        return pem(der(0x30, algorithm + der(0x03, Data([0]) + raw)))
    }
    static func pem(_ data: Data) -> String {
        let b = Array(data.base64EncodedString())
        return "-----BEGIN PUBLIC KEY-----\n" + stride(from: 0, to: b.count, by: 64).map { String(b[$0..<min($0+64,b.count)]) }.joined(separator: "\n") + "\n-----END PUBLIC KEY-----\n"
    }
    public static func parse(_ input: String) throws -> SecKey {
        let value = try normalizeArtifact(input)
        let compact = value.replacingOccurrences(of: "-----BEGIN PUBLIC KEY-----", with: "").replacingOccurrences(of: "-----END PUBLIC KEY-----", with: "").filter { !$0.isWhitespace }
        guard let data = Data(base64Encoded: compact), data.count <= 2048 else { throw FSTError.invalid("Invalid RSA public key") }
        var error: Unmanaged<CFError>?
        guard let key = SecKeyCreateWithData(data as CFData, [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPublic] as CFDictionary, &error),
              let attrs = SecKeyCopyAttributes(key) as? [CFString: Any], let bits = attrs[kSecAttrKeySizeInBits] as? Int, bits >= 2048 && bits <= 4096 else {
            throw FSTError.invalid("Expected an RSA public key (2048–4096 bits)")
        }
        return key
    }
    static func normalizeArtifact(_ input: String) throws -> String {
        let s = input.trimmingCharacters(in: .whitespacesAndNewlines.union(CharacterSet(charactersIn: "\u{FEFF}")))
        try require(s.utf8.count <= 16384, "Public key payload is too large")
        if s.hasPrefix("FST-PUB1:") { return String(s.dropFirst(9)) }
        if s.hasPrefix("{") {
            guard let json = try JSONSerialization.jsonObject(with: Data(s.utf8)) as? [String: Any] else { throw FSTError.invalid("Invalid key artifact") }
            if let type = json["artifactType"] as? String { try require(type == "FST-KEY1" && json["keyType"] as? String == "public") }
            if let key = json["publicKey"] as? String { return key }
            if let qr = json["qrText"] as? String, !qr.hasPrefix("{") { return try normalizeArtifact(qr) }
            throw FSTError.invalid("Missing public key")
        }
        return s
    }
    public static func normalize(_ input: String) throws -> String { try export(parse(input)) }
    public static func artifact(_ pem: String) throws -> String {
        let normalized = try normalize(pem)
        return "FST-PUB1:" + normalized.components(separatedBy: "\n").filter { !$0.hasPrefix("---") }.joined()
    }
    public static func fingerprint(_ pem: String) throws -> String {
        SHA256.hash(data: Data(try normalize(pem).utf8)).map { String(format: "%02x", $0) }.joined()
    }
    public static func wrap(_ data: Data, key: SecKey) throws -> Data {
        var error: Unmanaged<CFError>?
        guard let result = SecKeyCreateEncryptedData(key, .rsaEncryptionOAEPSHA256, data as CFData, &error) as Data? else { throw error!.takeRetainedValue() }
        return result
    }
    public static func unwrap(_ data: Data, key: SecKey) throws -> Data {
        var error: Unmanaged<CFError>?
        guard let result = SecKeyCreateDecryptedData(key, .rsaEncryptionOAEPSHA256, data as CFData, &error) as Data? else { throw error!.takeRetainedValue() }
        try require(result.count == 32); return result
    }
}
