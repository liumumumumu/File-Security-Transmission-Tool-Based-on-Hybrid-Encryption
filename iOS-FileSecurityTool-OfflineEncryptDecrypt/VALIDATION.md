# Validation record

Verified on 2026-11-23 using Xcode 26.2 (17C52), Swift 5 language mode, and an iPhone 17 Pro simulator running iOS 26.2.

| Check | Result |
| --- | --- |
| Swift Package core suite | 6 tests passed |
| iPhone UI smoke suite | 2 tests passed |
| Debug / iOS Simulator build | Passed |
| Release / generic physical iPhone build, signing disabled | Passed |
| Git whitespace check | Passed |
| Android source icon vs. iOS icon SHA-256 | Identical |

Core coverage includes canonical and malicious CBOR, RSA public-key conversions, Java and Android fixed FST2/FST-TEXT1 decryption vectors, iOS round trips, empty / 1 MiB / multi-block file boundaries, authentication failures, truncated and trailing input, wrong-recipient keys, path sanitization, and cancellation cleanup.

UI checks cover the Chinese workspace, English file/text switching, disabled decryption without a local key, empty/invalid contact validation, key-page navigation, and settings navigation. A composite file-picker accessibility label was corrected after the first run; both final UI tests pass.

Release compilation uses the physical-device SDK but does not install or run on a physical iPhone. Device authentication, passcode-bound Keychain behavior, real-camera scanning, document-provider behavior, and live Android/Java ↔ iPhone exchange remain manual acceptance items documented in README.md. Fixed incoming reference vectors pass; live outgoing cross-device verification has not been performed.

The only remaining app-build warning is Xcode's skipped App Intents metadata extraction because the app has no App Intents dependency. No third-party dependencies or production signing material are required or included.

Icon SHA-256: `2dfa9fd780a36da6ec01dfd12cce4a6e3ae1f8ee447d231d2c109dc849fbe1e4`.
