import Foundation
import CryptoKit
import Security

public enum FSTCrypto {
    static let prefix = Data([70,83,84,50,1,1,1,1])
    static let textPrefix = "FST-TEXT1:"
    static let chunkSize = 1_048_576
    static func random(_ n: Int) throws -> Data {
        var data = Data(count: n)
        let status = data.withUnsafeMutableBytes { SecRandomCopyBytes(kSecRandomDefault, n, $0.baseAddress!) }
        try require(status == errSecSuccess, "Secure random generation failed"); return data
    }
    static func nonce(_ seed: Data, _ domain: String, _ index: Int) -> Data {
        Data(HMAC<SHA256>.authenticationCode(for: Data(domain.utf8) + Data([0]) + Data.u32(index), using: SymmetricKey(data: seed))).prefix(12)
    }
    static func seal(_ plain: Data, _ key: Data, _ nonce: Data, _ aad: Data) throws -> (Data, Data) {
        let box = try AES.GCM.seal(plain, using: SymmetricKey(data: key), nonce: AES.GCM.Nonce(data: nonce), authenticating: aad)
        return (box.ciphertext, box.tag)
    }
    static func open(_ cipher: Data, _ tag: Data, _ key: Data, _ nonce: Data, _ aad: Data) throws -> Data {
        try require(key.count == 32 && nonce.count == 12 && tag.count == 16)
        return try AES.GCM.open(AES.GCM.SealedBox(nonce: AES.GCM.Nonce(data: nonce), ciphertext: cipher, tag: tag), using: SymmetricKey(data: key), authenticating: aad)
    }
    static func textAAD(_ wrapped: Data, _ nonce: Data, _ size: Int) -> [String: CBOR.Value] {
        ["protocol": .text(textPrefix), "version": .number(1), "keyWrapAlg": .number(1), "contentAlg": .number(1), "encryptedSessionKey": .bytes(wrapped), "nonce": .bytes(nonce), "plaintextLength": .number(size)]
    }
    public static func encryptText(_ text: String, recipient: String) throws -> String {
        let plain = Data(text.utf8)
        try require(!text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && plain.count <= 16384, "Enter text up to 16 KiB")
        let key = try random(32), nonce = try random(12)
        let wrapped = try PublicKey.wrap(key, key: PublicKey.parse(recipient))
        var map = textAAD(wrapped, nonce, plain.count)
        let (cipher, tag) = try seal(plain, key, nonce, CBOR.encode(map))
        map.removeValue(forKey: "protocol"); map["ciphertext"] = .bytes(cipher); map["tag"] = .bytes(tag)
        return textPrefix + CBOR.encode(map).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }
    public static func decryptText(_ text: String, privateKey: SecKey) throws -> String {
        let text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        try require(text.hasPrefix(textPrefix), "Expected FST-TEXT1 text")
        var encoded = text.dropFirst(textPrefix.count).filter { !$0.isWhitespace }.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        try require(encoded.count <= 65536)
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let data = Data(base64Encoded: encoded) else { throw FSTError.invalid("Invalid Base64URL") }
        let map = try CBOR.decode(data)
        try require(map.number("version") == 1 && map.number("keyWrapAlg") == 1 && map.number("contentAlg") == 1)
        let wrapped = try map.bytes("encryptedSessionKey"), nonce = try map.bytes("nonce"), size = try map.number("plaintextLength")
        let cipher = try map.bytes("ciphertext")
        try require(size > 0 && size <= 16384 && cipher.count == size)
        let key = try PublicKey.unwrap(wrapped, key: privateKey)
        let plain = try open(cipher, map.bytes("tag"), key, nonce, CBOR.encode(textAAD(wrapped, nonce, size)))
        guard let result = String(data: plain, encoding: .utf8) else { throw FSTError.invalid("Invalid UTF-8") }; return result
    }

    // Streams one MiB at a time. Call on a worker task; output must be a private staging URL.
    public static func processFile(input: URL, output: URL, recipient: String?, privateKey: SecKey?, progress: (Double) -> Void = { _ in }) throws -> String {
        let source = try FileHandle(forReadingFrom: input)
        defer { try? source.close() }
        try require(FileManager.default.createFile(atPath: output.path, contents: nil), "Cannot create output")
        let target = try FileHandle(forWritingTo: output)
        var completed = false
        defer { try? target.close(); if !completed { try? FileManager.default.removeItem(at: output) } }
        func read(_ n: Int) throws -> Data {
            try require(n >= 0 && n <= chunkSize)
            var result = Data()
            while result.count < n {
                let part = try source.read(upToCount: n - result.count) ?? Data()
                try require(!part.isEmpty, "Truncated file"); result += part
            }
            return result
        }
        func integer() throws -> Int { try read(4).reduce(0) { ($0 << 8) | Int($1) } }
        func field(_ max: Int) throws -> Data { let n = try integer(); try require(n <= max); return try read(n) }
        let size: Int, blockSize: Int, blocks: Int, key: Data, seed: Data, name: String
        if let recipient {
            let fileSize = try source.seekToEnd(); try source.seek(toOffset: 0)
            try require(fileSize <= UInt64(Int.max), "File too large")
            size = Int(fileSize); blockSize = chunkSize; blocks = size == 0 ? 0 : (size - 1) / blockSize + 1
            try require(blocks <= Int(Int32.max), "File too large")
            key = try random(32); seed = try random(32); name = input.lastPathComponent
            let wrapped = try PublicKey.wrap(key, key: PublicKey.parse(recipient))
            let header = CBOR.encode(["fileName": .text(name), "fileSize": .number(size), "chunkSizeBytes": .number(blockSize), "totalBlocks": .number(blocks)])
            let aad = prefix + wrapped.field + seed.field + Data.u32(header.count)
            let (cipher, tag) = try seal(header, key, nonce(seed, "FST2-header", 0), aad)
            try target.write(contentsOf: aad + cipher + tag.field)
        } else {
            try require(read(8) == prefix, "Unsupported FST2 file")
            let wrapped = try field(1024)
            seed = try field(32); try require(seed.count == 32)
            let header = try field(65536), tag = try field(16)
            guard let privateKey else { throw FSTError.invalid("Create a local key first") }
            key = try PublicKey.unwrap(wrapped, key: privateKey)
            let aad = prefix + wrapped.field + seed.field + Data.u32(header.count)
            let map = try CBOR.decode(open(header, tag, key, nonce(seed, "FST2-header", 0), aad))
            size = try map.number("fileSize"); blockSize = try map.number("chunkSizeBytes"); blocks = try map.number("totalBlocks")
            name = try map.text("fileName")
            try require(blockSize > 0 && blockSize <= chunkSize && blocks <= Int(Int32.max))
            try require(blocks == (size == 0 ? 0 : (size - 1) / blockSize + 1))
        }
        var processed = 0
        for index in 0..<blocks {
            try Task.checkCancellation()
            let length = min(blockSize, size - processed)
            let aad = prefix + Data.u32(index) + Data.u32(length) + Data.u32(length)
            if recipient != nil {
                let plain = try read(length)
                let (cipher, tag) = try seal(plain, key, nonce(seed, "FST2-block", index), aad)
                try target.write(contentsOf: Data.u32(index) + Data.u32(length) + cipher.field + tag.field)
            } else {
                try require(integer() == index && integer() == length)
                let cipher = try field(blockSize); try require(cipher.count == length)
                let tag = try field(16)
                try target.write(contentsOf: open(cipher, tag, key, nonce(seed, "FST2-block", index), aad))
            }
            processed += length; progress(Double(processed) / Double(max(size, 1)))
        }
        try Task.checkCancellation()
        try require((try source.read(upToCount: 1) ?? Data()).isEmpty, "Unexpected trailing data")
        try target.synchronize(); completed = true; progress(1)
        return recipient != nil ? UUID().uuidString + ".FST2" : safeName(name)
    }
    public static func safeName(_ name: String) -> String {
        let name = name.replacingOccurrences(of: "\\", with: "/").components(separatedBy: "/").last ?? ""
        let cleaned = String(name.unicodeScalars.filter { !CharacterSet.controlCharacters.contains($0) && $0 != ":" }.prefix(180))
        return cleaned.isEmpty || cleaned == "." || cleaned == ".." ? "decrypted-file" : cleaned
    }
}
