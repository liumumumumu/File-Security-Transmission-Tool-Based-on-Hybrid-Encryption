import Foundation

public enum FSTError: LocalizedError {
    case invalid(String)
    public var errorDescription: String? {
        switch self { case .invalid(let message): return message }
    }
}
func require(_ condition: Bool, _ message: String = "Invalid or damaged encrypted content") throws {
    if !condition { throw FSTError.invalid(message) }
}
extension Data {
    static func u32(_ value: Int) -> Data {
        var value = UInt32(value).bigEndian
        return Swift.withUnsafeBytes(of: &value) { Data($0) }
    }
    var field: Data { Self.u32(count) + self }
}
struct BinaryReader {
    let data: Data
    var offset = 0
    mutating func read(_ length: Int) throws -> Data {
        try require(length >= 0 && length <= data.count - offset)
        defer { offset += length }
        return data.subdata(in: offset..<offset + length)
    }
    mutating func integer() throws -> Int { try read(4).reduce(0) { ($0 << 8) | Int($1) } }
    mutating func field(max: Int = 1_048_576) throws -> Data {
        let n = try integer(); try require(n <= max); return try read(n)
    }
}
// Strict, bounded canonical CBOR subset used by the FST protocols.
enum CBOR {
    enum Value { case number(Int), bytes(Data), text(String) }
    static func head(_ major: UInt8, _ n: Int) -> Data {
        if n < 24 { return Data([major << 5 | UInt8(n)]) }
        if n <= 255 { return Data([major << 5 | 24, UInt8(n)]) }
        if n <= 65535 { return Data([major << 5 | 25, UInt8(n >> 8), UInt8(n & 255)]) }
        if n <= Int(UInt32.max) { return Data([major << 5 | 26]) + Data.u32(n) }
        var v = UInt64(n).bigEndian
        return Data([major << 5 | 27]) + withUnsafeBytes(of: &v) { Data($0) }
    }
    static func string(_ s: String) -> Data { head(3, s.utf8.count) + Data(s.utf8) }
    static func encode(_ map: [String: Value]) -> Data {
        var result = head(5, map.count)
        for key in map.keys.sorted(by: { string($0).lexicographicallyPrecedes(string($1)) }) {
            result += string(key)
            switch map[key]! {
            case .number(let n): result += head(0, n)
            case .bytes(let b): result += head(2, b.count) + b
            case .text(let s): result += string(s)
            }
        }
        return result
    }
    static func decode(_ data: Data) throws -> [String: Value] {
        var r = BinaryReader(data: data)
        func item(_ r: inout BinaryReader) throws -> (Int, Int) {
            let b = try r.read(1).first!; let a = Int(b & 31)
            if a < 24 { return (Int(b >> 5), a) }
            try require(a <= 27)
            let bytes = try r.read(1 << (a - 24))
            var n: UInt64 = 0
            for byte in bytes { n = (n << 8) | UInt64(byte) }
            try require(n <= UInt64(Int.max)); return (Int(b >> 5), Int(n))
        }
        let (major, count) = try item(&r); try require(major == 5 && count <= 32)
        var result: [String: Value] = [:]
        for _ in 0..<count {
            let (k, n) = try item(&r); try require(k == 3)
            guard let key = String(data: try r.read(n), encoding: .utf8) else { throw FSTError.invalid("Invalid UTF-8") }
            try require(result[key] == nil)
            let (type, size) = try item(&r)
            switch type {
            case 0: result[key] = .number(size)
            case 2: result[key] = .bytes(try r.read(size))
            case 3:
                guard let s = String(data: try r.read(size), encoding: .utf8) else { throw FSTError.invalid("Invalid UTF-8") }
                result[key] = .text(s)
            default: throw FSTError.invalid("Unsupported CBOR field")
            }
        }
        try require(r.offset == data.count); return result
    }
}
extension Dictionary where Key == String, Value == CBOR.Value {
    func number(_ key: String) throws -> Int { guard case .number(let n) = self[key] else { throw FSTError.invalid("Missing \(key)") }; return n }
    func bytes(_ key: String) throws -> Data { guard case .bytes(let b) = self[key] else { throw FSTError.invalid("Missing \(key)") }; return b }
    func text(_ key: String) throws -> String { guard case .text(let s) = self[key] else { throw FSTError.invalid("Missing \(key)") }; return s }
}
