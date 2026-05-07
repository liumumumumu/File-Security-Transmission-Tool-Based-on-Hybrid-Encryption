#include <QCoreApplication>
#include <QCommandLineOption>
#include <QCommandLineParser>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonValue>
#include <QScopeGuard>
#include <QHash>
#include <QSharedPointer>
#include <QtNetwork/QHostAddress>
#include <QtNetwork/QTcpServer>
#include <QtNetwork/QTcpSocket>

#include <openssl/bio.h>
#include <openssl/buffer.h>
#include <openssl/evp.h>
#include <openssl/pem.h>
#include <openssl/rand.h>
#include <openssl/rsa.h>
#include <openssl/sha.h>

#include <cstdio>
#include <functional>
#include <stdexcept>
#include <utility>

struct ServiceConfig {
    QHostAddress host = QHostAddress::Any;
    quint16 port = 9080;
    QString keyDir = QStringLiteral("./crypto_keys");
};

struct HttpRequest {
    QString method;
    QString path;
    QByteArray body;
    QHostAddress remoteAddress;
};

struct HttpResponse {
    int status = 200;
    QJsonObject body;
};

using Handler = std::function<HttpResponse(const HttpRequest&)>;

static QString keyDir;
static QString privateKeyFile;
static QString publicKeyFile;

static void logLine(const char* level, const QString& message)
{
    const QString line = QStringLiteral("[%1] [%2] %3")
        .arg(QDateTime::currentDateTime().toString(QStringLiteral("yyyy-MM-dd HH:mm:ss")))
        .arg(QString::fromLatin1(level), message);

    FILE* stream = qstrcmp(level, "ERROR") == 0 ? stderr : stdout;
    std::fprintf(stream, "%s\n", line.toUtf8().constData());
    std::fflush(stream);
}

static void logInfo(const QString& message) { logLine("INFO", message); }
static void logWarn(const QString& message) { logLine("WARN", message); }
static void logError(const QString& message) { logLine("ERROR", message); }

static void setKeyDir(const QString& dir)
{
    keyDir = dir;
    privateKeyFile = QDir(keyDir).filePath(QStringLiteral("private_key.pem"));
    publicKeyFile = QDir(keyDir).filePath(QStringLiteral("public_key.pem"));

    logInfo(QStringLiteral("key directory configured key_dir=%1 private_key_file=%2 public_key_file=%3")
        .arg(keyDir, privateKeyFile, publicKeyFile));
}

static ServiceConfig parseArgs(const QCoreApplication& app)
{
    QCommandLineParser parser;
    parser.setApplicationDescription(QStringLiteral("Qt crypto file security transfer service"));
    parser.addHelpOption();

    QCommandLineOption hostOption(QStringLiteral("host"), QStringLiteral("Listen host, default 0.0.0.0"), QStringLiteral("host"), QStringLiteral("0.0.0.0"));
    QCommandLineOption portOption(QStringLiteral("port"), QStringLiteral("Listen port, default 9080"), QStringLiteral("port"), QStringLiteral("9080"));
    QCommandLineOption keyDirOption(QStringLiteral("key-dir"), QStringLiteral("Key storage directory, default ./crypto_keys"), QStringLiteral("dir"), QStringLiteral("./crypto_keys"));

    parser.addOption(hostOption);
    parser.addOption(portOption);
    parser.addOption(keyDirOption);
    parser.process(app);

    ServiceConfig config;
    const QString hostText = parser.value(hostOption);
    if (!config.host.setAddress(hostText)) {
        throw std::runtime_error(QStringLiteral("Invalid host: %1").arg(hostText).toStdString());
    }

    bool portOk = false;
    const int port = parser.value(portOption).toInt(&portOk);
    if (!portOk || port <= 0 || port > 65535) {
        throw std::runtime_error("Port must be between 1 and 65535");
    }

    config.port = static_cast<quint16>(port);
    config.keyDir = parser.value(keyDirOption);
    return config;
}

static QByteArray base64Encode(const QByteArray& data)
{
    return data.toBase64(QByteArray::Base64Encoding);
}

static QByteArray base64Decode(const QString& text)
{
    return QByteArray::fromBase64(text.toUtf8(), QByteArray::Base64Encoding);
}

static QByteArray readTextFile(const QString& path)
{
    logInfo(QStringLiteral("reading text file path=%1").arg(path));
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly)) {
        throw std::runtime_error(QStringLiteral("Cannot read file: %1").arg(path).toStdString());
    }
    const QByteArray content = file.readAll();
    logInfo(QStringLiteral("read text file path=%1 bytes=%2").arg(path).arg(content.size()));
    return content;
}

static void writeTextFile(const QString& path, const QByteArray& content)
{
    logInfo(QStringLiteral("writing text file path=%1 bytes=%2").arg(path).arg(content.size()));
    QFile file(path);
    if (!file.open(QIODevice::WriteOnly | QIODevice::Truncate)) {
        throw std::runtime_error(QStringLiteral("Cannot write file: %1").arg(path).toStdString());
    }
    if (file.write(content) != content.size()) {
        throw std::runtime_error(QStringLiteral("Cannot write complete file: %1").arg(path).toStdString());
    }
    logInfo(QStringLiteral("wrote text file path=%1").arg(path));
}

static EVP_PKEY* loadPrivateKey()
{
    logInfo(QStringLiteral("loading private key path=%1").arg(privateKeyFile));
    FILE* fp = std::fopen(privateKeyFile.toLocal8Bit().constData(), "rb");
    if (!fp) {
        throw std::runtime_error("Private key not found");
    }

    EVP_PKEY* key = PEM_read_PrivateKey(fp, nullptr, nullptr, nullptr);
    std::fclose(fp);
    if (!key) {
        throw std::runtime_error("Failed to read private key");
    }
    return key;
}

static EVP_PKEY* publicKeyFromPem(const QByteArray& pem)
{
    BIO* bio = BIO_new_mem_buf(pem.constData(), pem.size());
    EVP_PKEY* key = PEM_read_bio_PUBKEY(bio, nullptr, nullptr, nullptr);
    BIO_free(bio);
    if (!key) {
        throw std::runtime_error("Invalid public key PEM");
    }
    return key;
}

static EVP_PKEY* privateKeyFromPem(const QByteArray& pem)
{
    BIO* bio = BIO_new_mem_buf(pem.constData(), pem.size());
    EVP_PKEY* key = PEM_read_bio_PrivateKey(bio, nullptr, nullptr, nullptr);
    BIO_free(bio);
    if (!key) {
        throw std::runtime_error("Invalid private key PEM");
    }
    return key;
}

static QByteArray privateKeyToPem(EVP_PKEY* key)
{
    BIO* bio = BIO_new(BIO_s_mem());
    auto cleanup = qScopeGuard([&] { BIO_free(bio); });
    if (PEM_write_bio_PrivateKey(bio, key, nullptr, nullptr, 0, nullptr, nullptr) != 1) {
        throw std::runtime_error("Failed to encode private key PEM");
    }

    BUF_MEM* mem = nullptr;
    BIO_get_mem_ptr(bio, &mem);
    return QByteArray(mem->data, static_cast<int>(mem->length));
}

static QByteArray publicKeyToPem(EVP_PKEY* key)
{
    BIO* bio = BIO_new(BIO_s_mem());
    auto cleanup = qScopeGuard([&] { BIO_free(bio); });
    if (PEM_write_bio_PUBKEY(bio, key) != 1) {
        throw std::runtime_error("Failed to encode public key PEM");
    }

    BUF_MEM* mem = nullptr;
    BIO_get_mem_ptr(bio, &mem);
    return QByteArray(mem->data, static_cast<int>(mem->length));
}

static QByteArray derivePublicKeyPemFromPrivateKeyPem(const QByteArray& privateKeyPem)
{
    EVP_PKEY* key = privateKeyFromPem(privateKeyPem);
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });
    return publicKeyToPem(key);
}

static QString derivePublicKeyFromPrivateKeyPem(const QString& privateKeyPem)
{
    return QString::fromUtf8(derivePublicKeyPemFromPrivateKeyPem(privateKeyPem.toUtf8()));
}

static void persistKeyPair(EVP_PKEY* key)
{
    QDir().mkpath(keyDir);
    writeTextFile(privateKeyFile, privateKeyToPem(key));
    writeTextFile(publicKeyFile, publicKeyToPem(key));
}

static QByteArray importPrivateKeyAndPersistKeyPair(const QByteArray& privateKeyPem)
{
    EVP_PKEY* key = privateKeyFromPem(privateKeyPem);
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    const QByteArray normalizedPrivateKeyPem = privateKeyToPem(key);
    const QByteArray derivedPublicKeyPem = publicKeyToPem(key);

    QDir().mkpath(keyDir);
    writeTextFile(privateKeyFile, normalizedPrivateKeyPem);
    writeTextFile(publicKeyFile, derivedPublicKeyPem);
    return derivedPublicKeyPem;
}

static void generateAndPersistKeyPair()
{
    logInfo(QStringLiteral("generating RSA key pair key_dir=%1").arg(keyDir));
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, nullptr);
    if (!ctx) {
        throw std::runtime_error("EVP_PKEY_CTX_new_id failed");
    }
    auto ctxCleanup = qScopeGuard([&] { EVP_PKEY_CTX_free(ctx); });

    if (EVP_PKEY_keygen_init(ctx) <= 0) {
        throw std::runtime_error("EVP_PKEY_keygen_init failed");
    }
    if (EVP_PKEY_CTX_set_rsa_keygen_bits(ctx, 2048) <= 0) {
        throw std::runtime_error("Set RSA bits failed");
    }

    EVP_PKEY* key = nullptr;
    if (EVP_PKEY_keygen(ctx, &key) <= 0) {
        throw std::runtime_error("RSA key generation failed");
    }
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    persistKeyPair(key);
    logInfo(QStringLiteral("generated RSA key pair private_key_file=%1 public_key_file=%2").arg(privateKeyFile, publicKeyFile));
}

static QByteArray randomBytes(int size)
{
    QByteArray data;
    data.resize(size);
    if (RAND_bytes(reinterpret_cast<unsigned char*>(data.data()), data.size()) != 1) {
        throw std::runtime_error("RAND_bytes failed");
    }
    return data;
}

static QString signData(const QString& data)
{
    EVP_PKEY* key = loadPrivateKey();
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    EVP_MD_CTX* mdctx = EVP_MD_CTX_new();
    if (!mdctx) {
        throw std::runtime_error("EVP_MD_CTX_new failed");
    }
    auto mdCleanup = qScopeGuard([&] { EVP_MD_CTX_free(mdctx); });

    EVP_PKEY_CTX* pctx = nullptr;
    if (EVP_DigestSignInit(mdctx, &pctx, EVP_sha256(), nullptr, key) <= 0) {
        throw std::runtime_error("DigestSignInit failed");
    }
    EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING);
    EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1);

    const QByteArray bytes = data.toUtf8();
    EVP_DigestSignUpdate(mdctx, bytes.constData(), bytes.size());

    size_t sigLen = 0;
    EVP_DigestSignFinal(mdctx, nullptr, &sigLen);
    QByteArray signature;
    signature.resize(static_cast<int>(sigLen));
    if (EVP_DigestSignFinal(mdctx, reinterpret_cast<unsigned char*>(signature.data()), &sigLen) <= 0) {
        throw std::runtime_error("DigestSignFinal failed");
    }
    signature.resize(static_cast<int>(sigLen));

    return QString::fromLatin1(base64Encode(signature));
}

static bool verifySignature(const QString& publicKeyPem, const QString& data, const QString& signatureBase64)
{
    EVP_PKEY* key = publicKeyFromPem(publicKeyPem.toUtf8());
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    const QByteArray signature = base64Decode(signatureBase64);
    EVP_MD_CTX* mdctx = EVP_MD_CTX_new();
    if (!mdctx) {
        throw std::runtime_error("EVP_MD_CTX_new failed");
    }
    auto mdCleanup = qScopeGuard([&] { EVP_MD_CTX_free(mdctx); });

    EVP_PKEY_CTX* pctx = nullptr;
    if (EVP_DigestVerifyInit(mdctx, &pctx, EVP_sha256(), nullptr, key) <= 0) {
        throw std::runtime_error("DigestVerifyInit failed");
    }
    EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING);
    EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1);

    const QByteArray bytes = data.toUtf8();
    EVP_DigestVerifyUpdate(mdctx, bytes.constData(), bytes.size());

    return EVP_DigestVerifyFinal(mdctx, reinterpret_cast<const unsigned char*>(signature.constData()), signature.size()) == 1;
}

static QString rsaEncrypt(const QString& publicKeyPem, const QString& plainBase64)
{
    EVP_PKEY* key = publicKeyFromPem(publicKeyPem.toUtf8());
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    const QByteArray plain = base64Decode(plainBase64);
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(key, nullptr);
    if (!ctx) {
        throw std::runtime_error("EVP_PKEY_CTX_new failed");
    }
    auto ctxCleanup = qScopeGuard([&] { EVP_PKEY_CTX_free(ctx); });

    EVP_PKEY_encrypt_init(ctx);
    EVP_PKEY_CTX_set_rsa_padding(ctx, RSA_PKCS1_OAEP_PADDING);
    EVP_PKEY_CTX_set_rsa_oaep_md(ctx, EVP_sha256());
    EVP_PKEY_CTX_set_rsa_mgf1_md(ctx, EVP_sha256());

    size_t outLen = 0;
    EVP_PKEY_encrypt(ctx, nullptr, &outLen, reinterpret_cast<const unsigned char*>(plain.constData()), plain.size());
    QByteArray cipher;
    cipher.resize(static_cast<int>(outLen));
    if (EVP_PKEY_encrypt(ctx, reinterpret_cast<unsigned char*>(cipher.data()), &outLen, reinterpret_cast<const unsigned char*>(plain.constData()), plain.size()) <= 0) {
        throw std::runtime_error("RSA encrypt failed");
    }
    cipher.resize(static_cast<int>(outLen));
    return QString::fromLatin1(base64Encode(cipher));
}

static QString rsaDecrypt(const QString& cipherBase64)
{
    EVP_PKEY* key = loadPrivateKey();
    auto keyCleanup = qScopeGuard([&] { EVP_PKEY_free(key); });

    const QByteArray cipher = base64Decode(cipherBase64);
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(key, nullptr);
    if (!ctx) {
        throw std::runtime_error("EVP_PKEY_CTX_new failed");
    }
    auto ctxCleanup = qScopeGuard([&] { EVP_PKEY_CTX_free(ctx); });

    EVP_PKEY_decrypt_init(ctx);
    EVP_PKEY_CTX_set_rsa_padding(ctx, RSA_PKCS1_OAEP_PADDING);
    EVP_PKEY_CTX_set_rsa_oaep_md(ctx, EVP_sha256());
    EVP_PKEY_CTX_set_rsa_mgf1_md(ctx, EVP_sha256());

    size_t outLen = 0;
    EVP_PKEY_decrypt(ctx, nullptr, &outLen, reinterpret_cast<const unsigned char*>(cipher.constData()), cipher.size());
    QByteArray plain;
    plain.resize(static_cast<int>(outLen));
    if (EVP_PKEY_decrypt(ctx, reinterpret_cast<unsigned char*>(plain.data()), &outLen, reinterpret_cast<const unsigned char*>(cipher.constData()), cipher.size()) <= 0) {
        throw std::runtime_error("RSA decrypt failed");
    }
    plain.resize(static_cast<int>(outLen));
    return QString::fromLatin1(base64Encode(plain));
}

static QJsonObject aesGcmEncrypt(const QString& keyBase64, const QString& plainBase64)
{
    const QByteArray key = base64Decode(keyBase64);
    const QByteArray plain = base64Decode(plainBase64);
    if (key.size() != 32) {
        throw std::runtime_error("AES-256 key must be 32 bytes");
    }

    const QByteArray nonce = randomBytes(12);
    QByteArray ciphertext;
    ciphertext.resize(plain.size());
    QByteArray tag;
    tag.resize(16);

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        throw std::runtime_error("EVP_CIPHER_CTX_new failed");
    }
    auto ctxCleanup = qScopeGuard([&] { EVP_CIPHER_CTX_free(ctx); });

    EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr);
    EVP_EncryptInit_ex(ctx, nullptr, nullptr, reinterpret_cast<const unsigned char*>(key.constData()), reinterpret_cast<const unsigned char*>(nonce.constData()));

    int len = 0;
    int ciphertextLen = 0;
    EVP_EncryptUpdate(ctx, reinterpret_cast<unsigned char*>(ciphertext.data()), &len, reinterpret_cast<const unsigned char*>(plain.constData()), plain.size());
    ciphertextLen = len;
    EVP_EncryptFinal_ex(ctx, reinterpret_cast<unsigned char*>(ciphertext.data()) + len, &len);
    ciphertextLen += len;
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, tag.size(), tag.data());
    ciphertext.resize(ciphertextLen);

    return {
        {QStringLiteral("nonce"), QString::fromLatin1(base64Encode(nonce))},
        {QStringLiteral("ciphertext"), QString::fromLatin1(base64Encode(ciphertext))},
        {QStringLiteral("tag"), QString::fromLatin1(base64Encode(tag))}
    };
}

static QString aesGcmDecrypt(const QString& keyBase64, const QString& nonceBase64, const QString& ciphertextBase64, const QString& tagBase64)
{
    const QByteArray key = base64Decode(keyBase64);
    const QByteArray nonce = base64Decode(nonceBase64);
    const QByteArray ciphertext = base64Decode(ciphertextBase64);
    const QByteArray tag = base64Decode(tagBase64);
    if (key.size() != 32) {
        throw std::runtime_error("AES-256 key must be 32 bytes");
    }
    if (tag.size() != 16) {
        throw std::runtime_error("AES-GCM tag must be 16 bytes");
    }

    QByteArray plain;
    plain.resize(ciphertext.size());
    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();
    if (!ctx) {
        throw std::runtime_error("EVP_CIPHER_CTX_new failed");
    }
    auto ctxCleanup = qScopeGuard([&] { EVP_CIPHER_CTX_free(ctx); });

    EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr);
    EVP_DecryptInit_ex(ctx, nullptr, nullptr, reinterpret_cast<const unsigned char*>(key.constData()), reinterpret_cast<const unsigned char*>(nonce.constData()));

    int len = 0;
    int plainLen = 0;
    EVP_DecryptUpdate(ctx, reinterpret_cast<unsigned char*>(plain.data()), &len, reinterpret_cast<const unsigned char*>(ciphertext.constData()), ciphertext.size());
    plainLen = len;
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, tag.size(), const_cast<char*>(tag.constData()));

    const int ok = EVP_DecryptFinal_ex(ctx, reinterpret_cast<unsigned char*>(plain.data()) + len, &len);
    if (ok <= 0) {
        throw std::runtime_error("AES-GCM tag verification failed");
    }
    plainLen += len;
    plain.resize(plainLen);
    return QString::fromLatin1(base64Encode(plain));
}

static QString fingerprint(const QString& publicKeyPem)
{
    const QByteArray bytes = publicKeyPem.toUtf8();
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256(reinterpret_cast<const unsigned char*>(bytes.constData()), bytes.size(), hash);

    QByteArray result;
    result.reserve(SHA256_DIGEST_LENGTH * 2);
    static const char* hex = "0123456789abcdef";
    for (unsigned char b : hash) {
        result.append(hex[b >> 4]);
        result.append(hex[b & 0x0f]);
    }
    return QString::fromLatin1(result);
}

static QJsonObject parseJsonBody(const HttpRequest& request)
{
    if (request.body.isEmpty()) {
        return {};
    }

    QJsonParseError error;
    const QJsonDocument document = QJsonDocument::fromJson(request.body, &error);
    if (error.error != QJsonParseError::NoError || !document.isObject()) {
        throw std::runtime_error(QStringLiteral("Invalid JSON body: %1").arg(error.errorString()).toStdString());
    }
    return document.object();
}

static QString requiredString(const QJsonObject& object, const QString& key)
{
    const QJsonValue value = object.value(key);
    if (!value.isString()) {
        throw std::runtime_error(QStringLiteral("Missing or invalid string field: %1").arg(key).toStdString());
    }
    return value.toString();
}

static HttpResponse ok(QJsonObject body)
{
    return {200, std::move(body)};
}

static HttpResponse errorResponse(const std::exception& e)
{
    logError(QStringLiteral("request failed error=\"%1\"").arg(QString::fromUtf8(e.what())));
    return {500, {{QStringLiteral("error"), QString::fromUtf8(e.what())}}};
}

static QByteArray httpStatusText(int status)
{
    switch (status) {
    case 200: return "OK";
    case 404: return "Not Found";
    case 409: return "Conflict";
    case 500: return "Internal Server Error";
    default: return "OK";
    }
}

static QByteArray buildHttpResponse(const HttpResponse& response)
{
    const QByteArray body = QJsonDocument(response.body).toJson(QJsonDocument::Compact);
    QByteArray raw;
    raw += "HTTP/1.1 " + QByteArray::number(response.status) + " " + httpStatusText(response.status) + "\r\n";
    raw += "Content-Type: application/json; charset=utf-8\r\n";
    raw += "Content-Length: " + QByteArray::number(body.size()) + "\r\n";
    raw += "Connection: close\r\n\r\n";
    raw += body;
    return raw;
}

static bool parseHttpRequest(const QByteArray& raw, QTcpSocket* socket, HttpRequest* request)
{
    const int headerEnd = raw.indexOf("\r\n\r\n");
    if (headerEnd < 0) {
        return false;
    }

    const QByteArray headerBytes = raw.left(headerEnd);
    const QList<QByteArray> headerLines = headerBytes.split('\n');
    if (headerLines.isEmpty()) {
        throw std::runtime_error("Invalid HTTP request");
    }

    const QList<QByteArray> requestLine = headerLines.first().trimmed().split(' ');
    if (requestLine.size() < 2) {
        throw std::runtime_error("Invalid HTTP request line");
    }

    int contentLength = 0;
    for (int i = 1; i < headerLines.size(); ++i) {
        const QByteArray line = headerLines.at(i).trimmed();
        const int separator = line.indexOf(':');
        if (separator <= 0) {
            continue;
        }
        const QByteArray name = line.left(separator).trimmed().toLower();
        if (name == "content-length") {
            contentLength = line.mid(separator + 1).trimmed().toInt();
        }
    }

    const int bodyStart = headerEnd + 4;
    if (raw.size() - bodyStart < contentLength) {
        return false;
    }

    request->method = QString::fromLatin1(requestLine.at(0));
    request->path = QString::fromUtf8(requestLine.at(1)).section('?', 0, 0);
    request->body = raw.mid(bodyStart, contentLength);
    request->remoteAddress = socket->peerAddress();
    return true;
}

static void registerRoutes(QHash<QString, Handler>* routes)
{
    routes->insert(QStringLiteral("GET /health"), [](const HttpRequest&) {
        return ok({{QStringLiteral("status"), QStringLiteral("ok")}, {QStringLiteral("keyDir"), keyDir}});
    });

    routes->insert(QStringLiteral("GET /key/public"), [](const HttpRequest&) {
        try {
            return ok({{QStringLiteral("publicKey"), QString::fromUtf8(readTextFile(publicKeyFile))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("GET /key/private"), [](const HttpRequest&) {
        try {
            return ok({{QStringLiteral("privateKey"), QString::fromUtf8(readTextFile(privateKeyFile))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("GET /key/status"), [](const HttpRequest&) {
        return ok({
            {QStringLiteral("hasPrivateKey"), QFile::exists(privateKeyFile) ? QStringLiteral("true") : QStringLiteral("false")},
            {QStringLiteral("hasPublicKey"), QFile::exists(publicKeyFile) ? QStringLiteral("true") : QStringLiteral("false")}
        });
    });

    routes->insert(QStringLiteral("POST /key/generate"), [](const HttpRequest&) {
        try {
            if (QFile::exists(privateKeyFile) || QFile::exists(publicKeyFile)) {
                logWarn(QStringLiteral("key generation rejected because key pair already exists"));
                return HttpResponse{409, {{QStringLiteral("success"), QStringLiteral("false")}, {QStringLiteral("error"), QStringLiteral("Key pair already exists")}}};
            }
            generateAndPersistKeyPair();
            return ok({
                {QStringLiteral("success"), QStringLiteral("true")},
                {QStringLiteral("privateKey"), QString::fromUtf8(readTextFile(privateKeyFile))},
                {QStringLiteral("publicKey"), QString::fromUtf8(readTextFile(publicKeyFile))}
            });
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /key/delete"), [](const HttpRequest&) {
        const bool deletedPrivate = QFile::exists(privateKeyFile) ? QFile::remove(privateKeyFile) : false;
        const bool deletedPublic = QFile::exists(publicKeyFile) ? QFile::remove(publicKeyFile) : false;
        return ok({
            {QStringLiteral("success"), QStringLiteral("true")},
            {QStringLiteral("deletedPrivateKey"), deletedPrivate ? QStringLiteral("true") : QStringLiteral("false")},
            {QStringLiteral("deletedPublicKey"), deletedPublic ? QStringLiteral("true") : QStringLiteral("false")}
        });
    });

    routes->insert(QStringLiteral("POST /sign"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("signature"), signData(requiredString(body, QStringLiteral("data")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /verify"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            const bool valid = verifySignature(
                requiredString(body, QStringLiteral("publicKey")),
                requiredString(body, QStringLiteral("data")),
                requiredString(body, QStringLiteral("signature")));
            return ok({{QStringLiteral("valid"), valid ? QStringLiteral("true") : QStringLiteral("false")}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /aes/generate"), [](const HttpRequest&) {
        try {
            return ok({{QStringLiteral("key"), QString::fromLatin1(base64Encode(randomBytes(32)))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /rsa/encrypt"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("cipher"), rsaEncrypt(requiredString(body, QStringLiteral("publicKey")), requiredString(body, QStringLiteral("plain")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /rsa/decrypt"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("plain"), rsaDecrypt(requiredString(body, QStringLiteral("cipher")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /aes-gcm/encrypt"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok(aesGcmEncrypt(requiredString(body, QStringLiteral("key")), requiredString(body, QStringLiteral("plain"))));
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /aes-gcm/decrypt"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("plain"), aesGcmDecrypt(
                requiredString(body, QStringLiteral("key")),
                requiredString(body, QStringLiteral("nonce")),
                requiredString(body, QStringLiteral("ciphertext")),
                requiredString(body, QStringLiteral("tag")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /key/fingerprint"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("fingerprint"), fingerprint(requiredString(body, QStringLiteral("publicKey")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /key/derive-public"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            return ok({{QStringLiteral("publicKey"), derivePublicKeyFromPrivateKeyPem(requiredString(body, QStringLiteral("privateKey")))}});
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /key/import-text"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            const QByteArray privateKeyPem = requiredString(body, QStringLiteral("privateKey")).toUtf8();
            const QByteArray publicKeyPem = importPrivateKeyAndPersistKeyPair(privateKeyPem);
            return ok({
                {QStringLiteral("success"), QStringLiteral("true")},
                {QStringLiteral("publicKey"), QString::fromUtf8(publicKeyPem)}
            });
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });

    routes->insert(QStringLiteral("POST /key/import-file"), [](const HttpRequest& request) {
        try {
            const QJsonObject body = parseJsonBody(request);
            const QByteArray privateKeyPem = readTextFile(requiredString(body, QStringLiteral("path")));
            const QByteArray publicKeyPem = importPrivateKeyAndPersistKeyPair(privateKeyPem);
            return ok({
                {QStringLiteral("success"), QStringLiteral("true")},
                {QStringLiteral("publicKey"), QString::fromUtf8(publicKeyPem)}
            });
        } catch (const std::exception& e) {
            return errorResponse(e);
        }
    });
}

int main(int argc, char *argv[])
{
    QCoreApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("FileSecurityTransferTool_CryptoQt"));

    ServiceConfig config;
    try {
        logInfo(QStringLiteral("crypto service starting"));
        config = parseArgs(app);
        setKeyDir(config.keyDir);
        QDir().mkpath(keyDir);
    } catch (const std::exception& e) {
        logError(QStringLiteral("startup failed error=\"%1\"").arg(QString::fromUtf8(e.what())));
        return 1;
    }

    QHash<QString, Handler> routes;
    registerRoutes(&routes);

    QTcpServer server;
    QObject::connect(&server, &QTcpServer::newConnection, &server, [&] {
        while (QTcpSocket* socket = server.nextPendingConnection()) {
            auto request = QSharedPointer<HttpRequest>::create();
            auto buffer = QSharedPointer<QByteArray>::create();
            QObject::connect(socket, &QTcpSocket::readyRead, socket, [socket, request, buffer, &routes] {
                try {
                    buffer->append(socket->readAll());
                    if (!parseHttpRequest(*buffer, socket, request.data())) {
                        return;
                    }
                    buffer->clear();

                    const QString routeKey = request->method + QStringLiteral(" ") + request->path;
                    logInfo(QStringLiteral("request method=%1 path=%2 remote=%3 body_bytes=%4")
                        .arg(request->method, request->path, request->remoteAddress.toString())
                        .arg(request->body.size()));

                    const Handler handler = routes.value(routeKey);
                    const HttpResponse response = handler
                        ? handler(*request)
                        : HttpResponse{404, {{QStringLiteral("error"), QStringLiteral("Not found")}}};

                    socket->write(buildHttpResponse(response));
                    socket->disconnectFromHost();
                    logInfo(QStringLiteral("http completed method=%1 path=%2 status=%3 response_body_bytes=%4")
                        .arg(request->method, request->path)
                        .arg(response.status)
                        .arg(QJsonDocument(response.body).toJson(QJsonDocument::Compact).size()));
                } catch (const std::exception& e) {
                    socket->write(buildHttpResponse(errorResponse(e)));
                    socket->disconnectFromHost();
                }
            });
            QObject::connect(socket, &QTcpSocket::disconnected, socket, &QTcpSocket::deleteLater);
        }
    });

    if (!server.listen(config.host, config.port)) {
        logError(QStringLiteral("failed to listen host=%1 port=%2 error=%3")
            .arg(config.host.toString())
            .arg(config.port)
            .arg(server.errorString()));
        return 1;
    }

    logInfo(QStringLiteral("crypto service listening url=http://%1:%2 key_dir=%3")
        .arg(config.host.toString())
        .arg(config.port)
        .arg(keyDir));

    return app.exec();
}
