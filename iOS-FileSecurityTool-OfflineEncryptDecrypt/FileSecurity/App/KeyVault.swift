import Foundation
import Security
import LocalAuthentication

/// RSA keys are protected by the device passcode and user presence in Keychain.
/// RSA is not a Secure Enclave algorithm; no hardware-RSA claim is made.
enum KeyVault {
    static let tag = Data("com.filesecuritytool.ios.offline.rsa.v1".utf8)
    static var query: [CFString: Any] {
        [kSecClass: kSecClassKey, kSecAttrApplicationTag: tag, kSecAttrKeyType: kSecAttrKeyTypeRSA]
    }
    static var exists: Bool {
        var q = query
        let context = LAContext(); context.interactionNotAllowed = true
        q[kSecUseAuthenticationContext] = context
        let status = SecItemCopyMatching(q as CFDictionary, nil)
        return status == errSecSuccess || status == errSecInteractionNotAllowed
    }
    static func generate() throws -> String {
        try require(!exists, "A local key already exists")
        var error: Unmanaged<CFError>?
        guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, .userPresence, &error) else { throw error!.takeRetainedValue() }
        let attributes: [CFString: Any] = [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeySizeInBits: 2048,
            kSecPrivateKeyAttrs: [kSecAttrIsPermanent: true, kSecAttrApplicationTag: tag, kSecAttrAccessControl: access]]
        guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &error), let publicKey = SecKeyCopyPublicKey(key) else {
            if let error { throw error.takeRetainedValue() }; throw FSTError.invalid("Configure a device passcode before creating a key")
        }
        return try PublicKey.export(publicKey)
    }
    static func privateKey() throws -> SecKey {
        var q = query
        let context = LAContext(); context.localizedReason = "Unlock your local key to decrypt content"
        q[kSecUseAuthenticationContext] = context
        q[kSecReturnRef] = true
        var result: CFTypeRef?
        let status = SecItemCopyMatching(q as CFDictionary, &result)
        guard status == errSecSuccess, let result else { throw FSTError.invalid(SecCopyErrorMessageString(status, nil) as String? ?? "Unable to unlock private key") }
        return result as! SecKey
    }
    static func remove() throws {
        _ = try privateKey() // Explicit device authentication before irreversible deletion.
        let status = SecItemDelete(query as CFDictionary)
        try require(status == errSecSuccess, "Unable to delete local key")
    }
}
