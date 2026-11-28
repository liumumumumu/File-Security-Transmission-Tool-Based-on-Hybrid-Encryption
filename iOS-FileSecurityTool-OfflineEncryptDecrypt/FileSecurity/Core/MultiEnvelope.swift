import CryptoKit
import Foundation
import Security

enum MultiEnvelope {
    static let textPrefix = "FST-TEXT2:"
    static let fileMagic = Data("FST3".utf8)
    static let chunk = 1 << 20

    struct Header {
        let bytes: Data
        let key: Data
        let prefix: Data

        func nonce(_ index: Int) -> Data { prefix + Data.u32(index) }
    }

    private static func magic(text: Bool) -> Data {
        Data((text ? "FSTT" : "FST3").utf8) + Data([1, 1, 1, 1])
    }

    private static func id(_ publicKey: SecKey, salt: Data) throws -> Data {
        let pem = try PublicKey.export(publicKey)
        let body = pem.split(separator: "\n").filter { !$0.hasPrefix("-----") }.joined()
        guard let der = Data(base64Encoded: body) else { throw FSTError.invalid("Invalid public key") }
        return Data(SHA256.hash(data: salt + der))
    }

    static func create(_ recipients: [String], text: Bool) throws -> Header {
        let key = try FSTCrypto.random(32)
        let salt = try FSTCrypto.random(32)
        let prefix = try FSTCrypto.random(8)
        var table = [Data: Data]()
        for (index, recipient) in recipients.enumerated() {
            do {
                let pub = try PublicKey.parse(recipient)
                let label = try id(pub, salt: salt)
                if table[label] != nil { continue }
                try require(table.count < 256, "Maximum 256 recipients")
                try require([256, 384, 512].contains(SecKeyGetBlockSize(pub)), "Unsupported RSA size")
                table[label] = try PublicKey.wrap(key, key: pub)
            } catch { throw FSTError.invalid("Recipient \(index + 1): \(error.localizedDescription)") }
        }
        try require(!table.isEmpty, "Select at least one recipient")
        var bytes = magic(text: text) + salt + Data.u32(table.count)
        for (label, wrapped) in table.sorted(by: { $0.key.lexicographicallyPrecedes($1.key) }) {
            bytes += label + wrapped.field
        }
        return Header(bytes: bytes + prefix, key: key, prefix: prefix)
    }

    private static func parse(text: Bool, privateKey: SecKey, read: (Int) throws -> Data) throws -> Header {
        var bytes = Data()
        func field(_ n: Int) throws -> Data {
            let value = try read(n)
            bytes += value
            return value
        }
        try require(try field(8) == magic(text: text), "Unsupported multi-recipient format")
        let salt = try field(32)
        guard let pub = SecKeyCopyPublicKey(privateKey) else { throw FSTError.invalid("Missing local public key") }
        let wanted = try id(pub, salt: salt)
        let count = try integer(field(4))
        try require((1...256).contains(count))
        var previous = Data()
        var matched: Data?
        for _ in 0..<count {
            let label = try field(32)
            try require(previous.lexicographicallyPrecedes(label))
            previous = label
            let length = try integer(field(4))
            try require([256, 384, 512].contains(length))
            let wrapped = try field(length)
            if label == wanted { matched = wrapped }
        }
        let prefix = try field(8)
        guard let matched else { throw FSTError.invalid("No entry matches the current public key") }
        return Header(bytes: bytes, key: try PublicKey.unwrap(matched, key: privateKey), prefix: prefix)
    }

    private static func integer(_ bytes: Data) throws -> Int {
        let n = bytes.reduce(UInt64(0)) { ($0 << 8) | UInt64($1) }
        try require(n <= UInt64(Int.max))
        return Int(n)
    }

    private static func encoded(_ data: Data) -> String {
        data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
    }

    static func encryptText(_ text: String, recipients: [String]) throws -> String {
        let plain = Data(text.utf8)
        try require((1...16384).contains(plain.count), "Text must contain 1–16384 UTF-8 bytes")
        let h = try create(recipients, text: true)
        let aad = h.bytes + Data.u32(plain.count)
        let (cipher, tag) = try FSTCrypto.seal(plain, h.key, h.nonce(0), aad)
        return textPrefix + encoded(aad + cipher + tag)
    }

    static func decryptText(_ text: String, privateKey: SecKey) throws -> String {
        let body = String(text.dropFirst(textPrefix.count))
        try require(body.utf8.count <= 208992)
        let padded = body.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/") + String(repeating: "=", count: (4-body.utf8.count%4)%4)
        guard let data = Data(base64Encoded: padded), encoded(data) == body else { throw FSTError.invalid("Invalid Base64URL") }
        var input = BinaryReader(data: data)
        let h = try parse(text: true, privateKey: privateKey) { try input.read($0) }
        let size = try input.integer()
        try require((1...16384).contains(size) && data.count - input.offset == size + 16)
        let plain = try FSTCrypto.open(input.read(size), input.read(16), h.key, h.nonce(0), h.bytes + Data.u32(size))
        guard let text = String(data: plain, encoding: .utf8) else { throw FSTError.invalid("Invalid UTF-8") }
        return text
    }

    static func processFile(input: URL, output: URL, recipients: [String]?, privateKey: SecKey?, progress: (Double) -> Void) throws -> String {
        let source = try FileHandle(forReadingFrom: input)
        defer { try? source.close() }
        func read(_ count: Int) throws -> Data {
            var bytes = Data()
            while bytes.count < count {
                let part = try source.read(upToCount: count - bytes.count) ?? Data()
                try require(!part.isEmpty, "Truncated file")
                bytes += part
            }
            return bytes
        }
        let h: Header, size: Int, name: String
        var start = Data()
        if let recipients {
            h = try create(recipients, text: false)
            let length = try source.seekToEnd()
            try require(length <= UInt64(Int32.max) * UInt64(chunk), "File too large")
            try source.seek(toOffset: 0)
            size = Int(length)
            var big = length.bigEndian
            let filename = Data(input.lastPathComponent.utf8)
            try require((1...4096).contains(filename.count))
            let metadata = withUnsafeBytes(of: &big) { Data($0) } + filename
            let aad = h.bytes + Data.u32(metadata.count)
            let (cipher, tag) = try FSTCrypto.seal(metadata, h.key, h.nonce(0), aad)
            start = aad + cipher + tag
            name = UUID().uuidString + ".FST3"
        } else {
            guard let privateKey else { throw FSTError.invalid("Missing local key") }
            h = try parse(text: false, privateKey: privateKey, read: read)
            let n = try integer(read(4))
            try require((9...4104).contains(n))
            let meta = try FSTCrypto.open(read(n), read(16), h.key, h.nonce(0), h.bytes + Data.u32(n))
            size = try integer(meta.prefix(8))
            try require(size <= Int(Int32.max) * chunk)
            guard let raw = String(data: meta.dropFirst(8), encoding: .utf8) else { throw FSTError.invalid("Invalid filename") }
            name = FSTCrypto.safeName(raw)
        }
        try require(FileManager.default.createFile(atPath: output.path, contents: nil))
        let target = try FileHandle(forWritingTo: output)
        var complete = false
        defer { try? target.close(); if !complete { try? FileManager.default.removeItem(at: output) } }
        try target.write(contentsOf: start)
        let hash = Data(SHA256.hash(data: h.bytes))
        var done = 0
        var index = 1
        while done < size {
            try Task.checkCancellation()
            let n = min(chunk, size - done)
            let aad = hash + Data.u32(index) + Data.u32(n)
            if recipients != nil {
                let (cipher, tag) = try FSTCrypto.seal(read(n), h.key, h.nonce(index), aad)
                try target.write(contentsOf: cipher + tag)
            } else {
                try target.write(contentsOf: FSTCrypto.open(read(n), read(16), h.key, h.nonce(index), aad))
            }
            done += n
            index += 1
            progress(Double(done) / Double(size))
        }
        try Task.checkCancellation()
        try require((try source.read(upToCount: 1) ?? Data()).isEmpty, "Unexpected trailing data")
        try target.synchronize()
        complete = true
        progress(1)
        return name
    }
}
