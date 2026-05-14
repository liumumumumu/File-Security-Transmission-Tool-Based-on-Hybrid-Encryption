# File Security Transmission Tool - TCP Module

A Java / Spring Boot / Netty based secure file transmission module. This module includes both client and server entry points. The same executable jar can start different roles by using different startup arguments.

The client provides two interfaces:

- An interactive console for connecting to the server, sending files, viewing tasks, managing contacts, and managing keys.
- A local HTTP API, listening on `127.0.0.1:20201` by default, for local programs or frontend clients.

The server provides TCP relay capabilities for authentication, online device routing, file transfer request forwarding, transfer cancellation, and retransmission protocol forwarding.

## Tech Stack

- Java 21
- Spring Boot 3.3.11
- Netty 4.1.119
- MyBatis
- Redis
- MySQL
- SQLite
- Maven

## Project Structure

```text
src/main/java/com/client       Client connection, console, HTTP API, and file transfer logic
src/main/java/com/server       Server TCP relay, authentication, routing, and persistence
src/main/java/com/common       Shared protocols, codecs, configuration, and utilities
src/main/java/com/session      Transfer task state model
src/main/resources             Spring Boot configuration
scripts                        macOS/Linux/Windows startup scripts
```

## Default Ports And Addresses

Client defaults:

```text
TCP server address:       82.156.228.71:9000
Auto connect to server:   true
Client HTTP:              127.0.0.1:20201
Crypto service address:   127.0.0.1:20202
Received file directory:  downloads-client-1
```

Server defaults:

```text
Server TCP:  0.0.0.0:9000
Server HTTP: 0.0.0.0:8080
Redis:       127.0.0.1:6379
MySQL:       127.0.0.1:3306/db_FileSecurityTransmission
```

## Build

```bash
mvn clean package
```

The generated jar is located at:

```text
target/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar
```

## Start The Client

macOS / Linux:

```bash
./scripts/start-client.sh
```

Windows:

```bat
scripts\start-client.bat
```

After startup, the client automatically connects to the default server `82.156.228.71:9000` and starts the local HTTP service at `127.0.0.1:20201`.

You can also start the client directly with the jar:

```bash
java -jar target/FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar --app.role=client --spring.profiles.active=client
```

## Start The Server

macOS / Linux:

```bash
./scripts/start-server.sh
```

Windows:

```bat
scripts\start-server.bat
```

Before starting the server, prepare Redis and MySQL, then run `FileSecurityTransmission.sql` to initialize the database tables.

## Common Environment Variables

Client:

```text
CLIENT_SERVER_HOST      Default: 82.156.228.71
CLIENT_SERVER_PORT      Default: 9000
NODE_AUTO_CONNECT       Default: true
CLIENT_HTTP_ADDRESS     Default: 127.0.0.1
CLIENT_HTTP_PORT        Default: 20201
TRANSFER_RECEIVE_DIR    Default: downloads-client-1
NODE_DEVICE_ID          Optional. If not set, the client generates and stores a local deviceId.
```

Server:

```text
SERVER_TCP_BIND_HOST    Default: 0.0.0.0
SERVER_TCP_BIND_PORT    Default: 9000
SERVER_HTTP_ADDRESS     Default: 0.0.0.0
SERVER_HTTP_PORT        Default: 8080
REDIS_HOST              Default: 127.0.0.1
REDIS_PORT              Default: 6379
MYSQL_HOST              Default: 127.0.0.1
MYSQL_PORT              Default: 3306
MYSQL_DATABASE          Default: db_FileSecurityTransmission
MYSQL_USERNAME          Default: root
MYSQL_PASSWORD          Default: 123456zxc@
```

## Console Commands

After starting the client, enter commands at the `fst>` prompt.

```text
help                                      Show help
language                                  Change the help screen language
status                                    Show client connection status
connect [host] [port]                     Connect and authenticate with the server
disconnect                                Disconnect from the server
send <filePath> <targetAccountId>         Send a file
incoming                                  List pending incoming transfer requests
accept <transferId>                       Accept an incoming transfer request
reject <transferId>                       Reject an incoming transfer request
cancel <taskId|transferId>                Cancel a transfer task
retransmit <taskId|transferId>            Request retransmission
retransmit-accept <transferId>            Accept a retransmission request
retransmit-reject <transferId>            Reject a retransmission request
tasks                                     List transfer tasks
task <taskId|transferId> [--once]         Watch task progress
open-received <taskId|transferId|fileName> Open the received file location. Use "" or '' around file names.
contacts                                  List contacts
contact-add <accountId> [alias]           Add or update a contact
contact-remove <contact-N|N>              Remove a contact
contact-show <contact-N|N>                Show contact details
blacklist                                 List blacklist records
blacklist-add <accountId> [reason]        Add a blacklist record
blacklist-add-contact <contact-N|N>       Add a contact to the blacklist
blacklist-remove <accountId>              Remove a blacklist record
search-user <accountId>                   Search for an online user
search-user-add <accountId> [alias]       Search for an online user and add it to contacts
public-key                                Show the local public key
public-key-fingerprint [publicKey]        Calculate a public key fingerprint
account-id [publicKey]                    Alias of public-key-fingerprint
key-info                                  Show crypto service key status
generate-key                              Generate a key pair
delete-key                                Delete the key pair
import-private-key <keyText>              Import private key text
import-private-key-file <path>            Import private key from a file
import-private-key-paste                  Paste a multi-line private key
exit                                      Exit the client
```

### Help Language

The console displays the `help` screen in English by default. Enter `language` to choose the language used by the `help` screen:

```text
language
1. English
2. Chinese
```

Choose `English` / `1` to use English help output. Choose `Chinese` / `2` to use Chinese help output. This setting only affects the `help` command screen and does not change other console output.

## Client HTTP API

Default base URL:

```text
http://127.0.0.1:20201
```

System and keys:

```text
GET  /api/system/status
GET  /api/system/key
POST /api/system/key/generate
POST /api/system/key/delete
POST /api/system/key/import-private
POST /api/system/key/fingerprint
POST /api/system/connect
POST /api/system/disconnect
GET  /api/system/connection-status
GET  /api/system/public-key
GET  /api/system/help
```

Sending and tasks:

```text
POST /api/send
GET  /api/send/tasks
GET  /api/send/tasks/{taskIdOrTransferId}
POST /api/send/tasks/{taskIdOrTransferId}/cancel
GET  /api/send/tasks/{taskIdOrTransferId}/events
```

Receiving:

```text
GET  /incoming
POST /accept
POST /reject
POST /retransmit
```

Examples:

```bash
curl http://127.0.0.1:20201/api/system/status

curl -X POST http://127.0.0.1:20201/api/send \
  -H 'Content-Type: application/json' \
  -d '{"filePath":"./example.zip","targetAccountId":"<accountId>"}'
```

## Local Data

The client stores local state under the user directory:

```text
~/.file-security-transmission/device-id
~/.file-security-transmission/transfer-history.json
~/.file-security-transmission/local-data.db
```

Description:

- `device-id` stores the local device ID.
- `transfer-history.json` stores transfer task history.
- `local-data.db` stores contacts, blacklist records, and other local data.

## Locate Received Files

Received files are saved to `downloads-client-1` by default. Use the following console command:

```text
open-received <taskId|transferId|"fileName">
```

This command reveals the received file in the system file manager:

- macOS: Finder
- Windows: File Explorer
- Linux: Default file manager

When the argument is a file name, wrap it in double quotes or single quotes:

```text
open-received "report.zip"
open-received 'report.zip'
```

## Startup Layout

Startup layout as of 2026-05-06:

```text
project-root/
├── scripts/
│   ├── start-client.sh  (macOS, Linux)
│   └── start-client.bat (Windows)
└── target/
    └── FileSecurityTransmissionToolBasedonHybridEncryption_TCPModule-1.0-SNAPSHOT.jar
```
