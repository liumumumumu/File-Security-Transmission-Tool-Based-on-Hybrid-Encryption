# Deployment Notes

This version uses Qt for the service framework, JSON, files, command line parsing,
and TCP networking. OpenSSL is still used for RSA, AES-GCM, signatures, and PEM
handling because Qt does not provide a complete replacement for those operations.

## Build

Preferred CMake build:

```bash
cmake -S . -B build -DCMAKE_PREFIX_PATH=/path/to/Qt
cmake --build build --config Release
```

The qmake project is also kept:

```bash
qmake FileSecurityTransferTool_CryptoQt.pro
make
```

## Runtime Dependencies

Windows:
- Deploy Qt runtime with `windeployqt`.
- Put OpenSSL DLLs next to the `.exe`, for example `libssl-3-x64.dll` and
  `libcrypto-3-x64.dll`.

Linux:
- Prefer system packages for Qt and OpenSSL.
- For AppImage/deb/rpm packaging, declare Qt Network and OpenSSL runtime
  dependencies.

macOS:
- Deploy Qt runtime with `macdeployqt`.
- Bundle OpenSSL `.dylib` files if the app links to a non-system OpenSSL.

## API Compatibility

The HTTP endpoints match the original version:

- `GET /health`
- `GET /key/public`
- `GET /key/private`
- `GET /key/status`
- `POST /key/generate`
- `POST /key/delete`
- `POST /sign`
- `POST /verify`
- `POST /aes/generate`
- `POST /rsa/encrypt`
- `POST /rsa/decrypt`
- `POST /aes-gcm/encrypt`
- `POST /aes-gcm/decrypt`
- `POST /key/fingerprint`
- `POST /key/import-text`
- `POST /key/import-file`
