# FileSecurityTransmission for Android

[简体中文](README.md) | [English](README_EN.md)

FileSecurityTransmission is a fully offline Android application for encrypting and decrypting files and text. It encrypts content for a recipient's public key without connecting to an application server, producing `FST2` files and `FST-TEXT1` messages compatible with the macOS/Java client.

> The project is under active development and cross-device validation. It must not be treated as an audited, production-ready cryptographic product.

## Features

- File encryption and decryption with output under `Downloads/FileSecurity/`
- Text encryption and decryption
- Cross-platform FST2 and FST-TEXT1 containers
- Local contact and public-key management
- Public-key QR scanning, import, and export
- Encryption to the device's own public key
- File-task progress, cancellation, foreground execution, and failed-output cleanup
- Chinese and English interfaces with light, dark, and system themes
- Android sharing support for incoming FST-TEXT1 text and FST2 files
- Private-key access protected by strong biometrics or device credentials

The application operates entirely offline. Camera permission is requested only when scanning a QR code, and notification permission is requested only when a background file task needs a foreground notification.

## Compatible Formats

### FST2 Files

FST2 uses chunked AES-256-GCM encryption. Its authenticated header stores the original filename, file size, chunk size, and chunk count. Each block uses an independently derived nonce and authenticates its index and lengths.

Encrypted containers follow the Java client's `<UUID>.FST2` naming rule and do not expose the original filename. The application restores the original name only after decryption and header authentication.

### FST-TEXT1 Messages

Encrypted text uses the following representation:

```text
FST-TEXT1:<Base64URL-encoded CBOR payload>
```

The maximum plaintext size is 16 KiB.

### Cryptographic Parameters

- RSA-2048
- RSA-OAEP with SHA-256 and MGF1-SHA256
- AES-256-GCM with a 128-bit authentication tag
- HMAC-SHA256 for FST2 nonce derivation
- SHA-256 public-key fingerprints

The reference protocol implementation is located at:

```text
/Users/zero/Documents/c_idea_code/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule_copy
```

## Key Security Model

Hardware-backed RSA in Android 12–14 cannot reliably execute the MGF1-SHA256 parameters required by the desktop protocol. To remain compatible with the Mac/Java client, the application uses this design:

1. Generate an RSA-2048 key pair with a standard cryptographic provider.
2. Encrypt the PKCS#8 private key using an AES-256-GCM key stored in Android Keystore.
3. Prefer StrongBox for the wrapping key and fall back to a TEE when StrongBox is unavailable.
4. Require strong biometric authentication or device credentials to unwrap the private key, with a five-minute authorization window.
5. Disable Android backup and system data migration.

Non-exportable hardware RSA keys created by older application versions cannot be migrated to this design. After upgrading, users must delete the legacy key, generate a new key, and redistribute the new public key. Deleting a key permanently prevents decryption of historical ciphertext encrypted for its corresponding public key.

## Requirements

- Android 12 / API 31 or later
- A secure device lock, required for private-key generation and use
- Android SDK 35
- JDK 21

The project contains one `app` module and uses this package name:

```text
com.filesecuritytool.android
```

## Build

Use the Gradle Wrapper included in the repository:

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The project does not include a production signing key. Never commit `.jks`, `.keystore`, or signing-configuration files.

## Tests and Checks

Run JVM unit tests:

```bash
./gradlew testDebugUnitTest
```

Run Android lint:

```bash
./gradlew lintDebug
```

Build and run the main local checks:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Run instrumentation tests with a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

JVM tests cover container round trips, malformed input, chunk boundaries, fragmented file reads, Java interoperability vectors, public-key payload compatibility, Room contact logic, and output filename handling. Before release, Mac↔Android text and file interoperability must still be validated on physical devices running Android 12–15.

## Project Structure

```text
app/src/main/kotlin/com/app/
├── core/
│   ├── crypto/       # Key protection, OAEP, public keys, and application crypto API
│   └── files/        # Downloads and MediaStore output
├── crypto/           # FST2, FST-TEXT1, AES-GCM, and CBOR
├── data/             # Room contacts and DataStore settings
├── feature/          # Screen ViewModels
├── service/          # Background file tasks
└── ui/               # Compose screens, QR support, and themes
```

Important entry points:

- `core/crypto/HardwareKeyStore.kt`: hardware-AES wrapping for the RSA private key
- `core/crypto/RsaOaep.kt`: RSA-OAEP fixed to SHA-256/MGF1-SHA256
- `crypto/OfflineCryptoService.kt`: FST2 and FST-TEXT1 containers
- `service/FileTaskCoordinator.kt`: single-file task coordination and output cleanup
- `MainActivity.kt`: Compose navigation, permissions, and device authentication

## Data and Output

- Contacts: local Room database
- Settings: local DataStore
- Key material: encrypted private key in application-private storage and wrapping key in Android Keystore
- Encrypted and decrypted output: `Downloads/FileSecurity/`
- Public-key QR images: `Downloads/FileSecurity/`

Input files are always retained. Incomplete MediaStore output is deleted when a task is cancelled or fails. Decrypted-output name collisions are resolved by appending `(1)`, `(2)`, and so on.

## Development Constraints

- Do not add application-server communication or business networking dependencies.
- Do not change FST2 or FST-TEXT1 without updating cross-platform compatibility tests.
- Do not rely on provider-default OAEP parameters; explicitly set SHA-256 and MGF1-SHA256.
- Do not commit local SDK paths, build output, signing keys, or private configuration.
- Keep the Room schemas under `app/schemas/` in version control.

See [`handOff/`](handOff/) for the complete design decisions and implementation history.
