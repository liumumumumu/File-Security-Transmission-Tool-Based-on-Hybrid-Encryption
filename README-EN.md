# File Security Transmission Tool Based on Hybrid Encryption

A working secure file transmission prototype based on hybrid encryption. It implements file transfer, text messaging, contact management, a local crypto service, a Web UI, and a desktop launcher. The project is organized into separate modules: Java handles the client/server transport path and local HTTP API, Python provides the local crypto service, Vue provides the frontend UI, and Electron provides the desktop entrypoint.

## Implemented Features

- Hybrid encrypted file transfer: AES-GCM encrypts file content, RSA-OAEP wraps session keys, and signatures plus public-key fingerprints support identity-related flows.
- Online TCP relay transfer: clients connect to a server that handles authentication, online device routing, and transfer request forwarding.
- Send/receive task management: progress tracking, cancellation, accept, and reject flows.
- Retransmission support: the receiver can request retransmission, and the sender can handle retransmission requests.
- Contacts and blacklist: local contact management, online user search, and blacklist controls.
- Local HTTP API: the Java client exposes `127.0.0.1:20201` by default for the Web UI or other local programs.
- Web / Desktop UI: the Vue frontend can run independently during development or be served by the Java client; Electron can package the client as a desktop application.
- Offline encryption/decryption: local file and text encryption/decryption without an online transfer session.

## Tech Stack

### TCP / Backend

- Java 21
- Spring Boot 3.3.11
- Netty 4.1.119
- Maven
- MyBatis
- Redis / MySQL / SQLite

### Crypto Module

- Python 3.10+
- FastAPI
- cryptography
- AES-256-GCM
- RSA-2048 / RSA-OAEP
- RSA-PSS signing and verification

### Frontend

- Vue 3
- Vite
- lucide icons

### Desktop

- Electron
- electron-builder
- Windows MSI / macOS DMG packaging entrypoints

## Project Structure

```text
.
├── README.md              # Language entrypoint
├── README-ZH.md           # Chinese project overview
├── README-EN.md           # English project overview
├── Encryption_module/     # Python crypto module and FastAPI crypto service
├── TCP_Module/            # Java client/server, TCP relay, local HTTP API
├── UI_Module/             # Vue 3 frontend
├── Desktop_Module/        # Electron desktop entrypoint and packaging config
├── WINDOWS_PACKAGING.md   # Windows packaging notes
└── LICENSE
```

## Architecture

```text
Vue UI / Electron Desktop
        |
        | HTTP 127.0.0.1:20201
        v
Java Client Backend
        |
        | HTTP 127.0.0.1:20202
        v
Python Crypto Service

Java Client Backend
        |
        | TCP 9000
        v
Java Relay Server
```

By default, the Java client connects to the configured TCP server at `82.156.228.71:9000`. For a fully local deployment, you can start the `TCP_Module` server separately and prepare Redis, MySQL, and the database schema.

## Encryption Logic and Implementation

The project uses a hybrid encryption flow: each transfer gets a symmetric session key, and the receiver's public key wraps that session key. Online AES-GCM encryption/decryption for file blocks is performed inside the Java client process. RSA key management, RSA-OAEP, signing, and signature verification are provided by the local Python FastAPI crypto service. This lets the Java transport module process high-frequency data blocks directly while reusing a separate service for key management and asymmetric operations.

### Key Model

Each client uses the Python crypto service to generate and store an RSA-2048 key pair. The public key is used for contact identity, receiver-side key wrapping, public-key fingerprint calculation, and signature verification. The private key remains in the local key directory and is used for signing and for unwrapping session keys sent to this client.

The Python crypto service provides:

- RSA key pair generation, import, deletion, and status checks.
- Local public-key retrieval.
- RSA-OAEP encryption/decryption for AES session keys.
- RSA-PSS signing and verification for authentication or handshake data.
- AES-GCM endpoints for algorithm validation and service-based calls.

### Online File Transfer Encryption

Online file transfer uses RSA for session-key handling and AES-GCM for file content. The file is not encrypted as one large blob. It is read in blocks, with a default block size of 1 MB.

```text
Sender
  1. Generate an AES-256 session key for this file transfer
  2. Encrypt the session key with the receiver RSA public key
  3. Split the file into blocks
  4. Encrypt each block with AES-GCM, producing nonce, ciphertext, and tag
  5. Send one FileOfferPacket and multiple FileBlockPacket packets

Receiver
  1. Read the encrypted AES session key from FileOfferPacket
  2. Decrypt the AES session key with the local RSA private key
  3. Decrypt each FileBlockPacket with nonce, ciphertext, tag, and AES key
  4. Rebuild the original file in blockId order
```

`FileOfferPacket` stores transfer-level metadata, including `transferId`, sender public key, receiver public key, encrypted AES session key, file name, file size, and total block count. `FileBlockPacket` stores one encrypted file block with `blockId`, `nonce`, `ciphertext`, `tag`, and `transferId`.

### Receiver Decryption Flow

After receiving a file offer, the receiver creates a receiving context and uses the local private key to unwrap the AES session key. For every incoming file block, the Java process uses AES-GCM to recover the plaintext block and write it to the target file. Task state tracks receive progress for the console, HTTP API, and UI.

### Identity, Signatures, and Fingerprints

Client-server authentication, direct peer handshakes, and QR-based direct connection flows use signing and signature verification. Public-key fingerprints are also used as part of account identity and contact recognition. This README describes how these capabilities are used at a project level; detailed commands and APIs are documented in the `TCP_Module` README.

### Offline File and Text Encryption

The project also implements offline encryption/decryption without an online transfer path.

The FST2 file payload structurally contains:

- Magic, version, and algorithm identifiers.
- RSA-encrypted AES session key.
- Nonce seed.
- Encrypted metadata header with original file name, file size, chunk size, and total block count.
- Encrypted data blocks with block index, plaintext length, ciphertext, and tag.

The FST-TEXT1 text payload structurally contains:

- `FST-TEXT1:` prefix.
- Base64URL-encoded CBOR payload.
- RSA-encrypted AES session key.
- Nonce, ciphertext, tag, and plaintext length.

Besides file transfer, text messages and QR-based direct connection flows reuse the same crypto building blocks: session keys, AES-GCM content encryption, RSA key wrapping, and signing/verification.

## Quick Start

### Requirements

- Java 21
- Maven 3.x
- Python 3.10+
- Node.js 18+
- npm
- Redis / MySQL: only required when running your own Java server

### 1. Start the Python Crypto Service

```bash
cd Encryption_module
pip install -r requirements.txt
python crypto_service/main.py --host 127.0.0.1 --port 20202 --key-dir crypto_keys
```

After startup, visit:

```text
http://127.0.0.1:20202/health
http://127.0.0.1:20202/docs
```

### 2. Start the Java Client Backend

```bash
cd TCP_Module
mvn clean package
./scripts/start-client.sh
```

The client starts a local HTTP service by default:

```text
http://127.0.0.1:20201/
```

By default, the client connects to `82.156.228.71:9000`. To use another server, configure `CLIENT_SERVER_HOST` and `CLIENT_SERVER_PORT`.

### 3. Start the Vue Frontend

```bash
cd UI_Module
npm install
npm run dev
```

The development server is available at:

```text
http://127.0.0.1:5173/
```

Vite proxies local API requests to the Java client backend at `127.0.0.1:20201`.

### 4. Optional: Start the Java Server

To run your own TCP server, prepare Redis and MySQL, then execute `TCP_Module/FileSecurityTransmission.sql` to initialize the database schema.

```bash
cd TCP_Module
./scripts/start-server.sh
```

### 5. Optional: Start the Electron Desktop Shell

```bash
cd Desktop_Module
npm install
npm run dev
```

Final installer packaging is documented outside the root README. See [Desktop_Module/README.md](Desktop_Module/README.md) and [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md).

## Module Documentation

- [Encryption_module/README.md](Encryption_module/README.md): Python crypto module, FastAPI crypto service, AES-GCM tests, and benchmark.
- [TCP_Module/README.md](TCP_Module/README.md): Java client/server, console commands, local HTTP API, environment variables, and packaging scripts.
- [UI_Module/README.md](UI_Module/README.md): Vue frontend development, API proxying, build flow, and static resource sync.
- [Desktop_Module/README.md](Desktop_Module/README.md): Electron development mode, runtime preparation, and desktop installer builds.
- [WINDOWS_PACKAGING.md](WINDOWS_PACKAGING.md): Windows packaging notes.

## Files and Keys

Runtime execution may generate key files, local databases, logs, received-file directories, and build artifacts. These files should not be committed to the repository. The repository `.gitignore` already excludes common key directories, PEM/KEY files, build outputs, and local runtime data.

The default local HTTP services bind to `127.0.0.1`. When running your own server, adjust database credentials, Redis configuration, service bind addresses, and ports for your deployment environment.
