//
//  main.cpp
//  FileSecurityTransferTool_CryptoTest
//
//  Created by zero on 2026/5/3.
//

#include "httplib.h"
#include <nlohmann/json.hpp>

#include <openssl/evp.h>
#include <openssl/pem.h>
#include <openssl/rand.h>
#include <openssl/sha.h>
#include <openssl/bio.h>
#include <openssl/buffer.h>

#include <chrono>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

using json = nlohmann::json;

static std::string KEY_DIR = "./crypto_keys";
static std::string PRIVATE_KEY_FILE = KEY_DIR + "/private_key.pem";
static std::string PUBLIC_KEY_FILE = KEY_DIR + "/public_key.pem";

static std::string nowText() {
    auto now = std::chrono::system_clock::now();
    std::time_t time = std::chrono::system_clock::to_time_t(now);
    std::tm tm{};

#if defined(_WIN32)
    localtime_s(&tm, &time);
#else
    localtime_r(&time, &tm);
#endif

    std::ostringstream out;
    out << std::put_time(&tm, "%Y-%m-%d %H:%M:%S");
    return out.str();
}

static void logLine(const std::string& level, const std::string& message, std::ostream& stream) {
    stream << "[" << nowText() << "] [" << level << "] " << message << std::endl;
}

static void logInfo(const std::string& message) {
    logLine("INFO", message, std::cout);
}

static void logWarn(const std::string& message) {
    logLine("WARN", message, std::cout);
}

static void logError(const std::string& message) {
    logLine("ERROR", message, std::cerr);
}

static void logRequest(const std::string& endpoint, const httplib::Request& req) {
    std::string remote = req.remote_addr.empty() ? "-" : req.remote_addr;
    logInfo(
        "request endpoint=" + endpoint +
        " method=" + req.method +
        " path=" + req.path +
        " remote=" + remote +
        " body_bytes=" + std::to_string(req.body.size())
    );
}

struct ServiceConfig {
    std::string host = "0.0.0.0";
    int port = 9080;
    std::string keyDir = "./crypto_keys";
};

static void setKeyDir(const std::string& keyDir) {
    KEY_DIR = keyDir;
    PRIVATE_KEY_FILE = (std::filesystem::path(KEY_DIR) / "private_key.pem").string();
    PUBLIC_KEY_FILE = (std::filesystem::path(KEY_DIR) / "public_key.pem").string();
    logInfo(
        "key directory configured key_dir=" + KEY_DIR +
        " private_key_file=" + PRIVATE_KEY_FILE +
        " public_key_file=" + PUBLIC_KEY_FILE
    );
}

static void printUsage(const char* programName) {
    std::cout
        << "Usage: " << programName << " [--host HOST] [--port PORT] [--key-dir DIR]\n"
        << "\n"
        << "Options:\n"
        << "  --host HOST      Listen host, default 0.0.0.0\n"
        << "  --port PORT      Listen port, default 9080\n"
        << "  --key-dir DIR    Key storage directory, default ./crypto_keys\n"
        << "  --help           Show this help text\n";
}

static ServiceConfig parseArgs(int argc, char* argv[]) {
    ServiceConfig config;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];

        if (arg == "--help" || arg == "-h") {
            printUsage(argv[0]);
            std::exit(0);
        }

        if (arg == "--host" && i + 1 < argc) {
            config.host = argv[++i];
            logInfo("parsed host argument host=" + config.host);
            continue;
        }

        if (arg == "--port" && i + 1 < argc) {
            config.port = std::stoi(argv[++i]);
            if (config.port <= 0 || config.port > 65535) {
                throw std::runtime_error("Port must be between 1 and 65535");
            }
            logInfo("parsed port argument port=" + std::to_string(config.port));
            continue;
        }

        if (arg == "--key-dir" && i + 1 < argc) {
            config.keyDir = argv[++i];
            logInfo("parsed key directory argument key_dir=" + config.keyDir);
            continue;
        }

        throw std::runtime_error("Unknown or incomplete argument: " + arg);
    }

    return config;
}

static std::string base64Encode(const std::vector<unsigned char>& data) {
    BIO* bio = BIO_new(BIO_s_mem());
    BIO* b64 = BIO_new(BIO_f_base64());
    BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
    bio = BIO_push(b64, bio);

    BIO_write(bio, data.data(), static_cast<int>(data.size()));
    BIO_flush(bio);

    BUF_MEM* bufferPtr = nullptr;
    BIO_get_mem_ptr(bio, &bufferPtr);

    std::string result(bufferPtr->data, bufferPtr->length);
    BIO_free_all(bio);
    return result;
}

static std::vector<unsigned char> base64Decode(const std::string& text) {
    BIO* bio = BIO_new_mem_buf(text.data(), static_cast<int>(text.size()));
    BIO* b64 = BIO_new(BIO_f_base64());
    BIO_set_flags(b64, BIO_FLAGS_BASE64_NO_NL);
    bio = BIO_push(b64, bio);

    std::vector<unsigned char> buffer(text.size());
    int length = BIO_read(bio, buffer.data(), static_cast<int>(buffer.size()));
    BIO_free_all(bio);

    if (length < 0) {
        throw std::runtime_error("Base64 decode failed");
    }

    buffer.resize(length);
    return buffer;
}

static std::string readTextFile(const std::string& path) {
    logInfo("reading text file path=" + path);
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        throw std::runtime_error("Cannot read file: " + path);
    }
    std::string content{
        std::istreambuf_iterator<char>(in),
        std::istreambuf_iterator<char>()
    };
    logInfo("read text file path=" + path + " bytes=" + std::to_string(content.size()));
    return content;
}

static void writeTextFile(const std::string& path, const std::string& content) {
    logInfo("writing text file path=" + path + " bytes=" + std::to_string(content.size()));
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) {
        throw std::runtime_error("Cannot write file: " + path);
    }
    out << content;
    logInfo("wrote text file path=" + path);
}

static EVP_PKEY* loadPrivateKey() {
    logInfo("loading private key path=" + PRIVATE_KEY_FILE);
    FILE* fp = fopen(PRIVATE_KEY_FILE.c_str(), "rb");
    if (!fp) {
        throw std::runtime_error("Private key not found");
    }

    EVP_PKEY* key = PEM_read_PrivateKey(fp, nullptr, nullptr, nullptr);
    fclose(fp);

    if (!key) {
        throw std::runtime_error("Failed to read private key");
    }

    logInfo("loaded private key path=" + PRIVATE_KEY_FILE);
    return key;
}

static EVP_PKEY* loadPublicKey() {
    logInfo("loading public key path=" + PUBLIC_KEY_FILE);
    FILE* fp = fopen(PUBLIC_KEY_FILE.c_str(), "rb");
    if (!fp) {
        throw std::runtime_error("Public key not found");
    }

    EVP_PKEY* key = PEM_read_PUBKEY(fp, nullptr, nullptr, nullptr);
    fclose(fp);

    if (!key) {
        throw std::runtime_error("Failed to read public key");
    }

    logInfo("loaded public key path=" + PUBLIC_KEY_FILE);
    return key;
}

static EVP_PKEY* publicKeyFromPem(const std::string& pem) {
    logInfo("parsing public key pem bytes=" + std::to_string(pem.size()));
    BIO* bio = BIO_new_mem_buf(pem.data(), static_cast<int>(pem.size()));
    EVP_PKEY* key = PEM_read_bio_PUBKEY(bio, nullptr, nullptr, nullptr);
    BIO_free(bio);

    if (!key) {
        throw std::runtime_error("Invalid public key PEM");
    }

    logInfo("parsed public key pem");
    return key;
}

static std::string privateKeyToPem(EVP_PKEY* key) {
    BIO* bio = BIO_new(BIO_s_mem());
    PEM_write_bio_PrivateKey(bio, key, nullptr, nullptr, 0, nullptr, nullptr);

    BUF_MEM* mem = nullptr;
    BIO_get_mem_ptr(bio, &mem);

    std::string pem(mem->data, mem->length);
    BIO_free(bio);
    return pem;
}

static std::string publicKeyToPem(EVP_PKEY* key) {
    BIO* bio = BIO_new(BIO_s_mem());
    PEM_write_bio_PUBKEY(bio, key);

    BUF_MEM* mem = nullptr;
    BIO_get_mem_ptr(bio, &mem);

    std::string pem(mem->data, mem->length);
    BIO_free(bio);
    return pem;
}

static void generateKeyPairIfMissing() {
    logInfo("checking key pair presence key_dir=" + KEY_DIR);
    std::filesystem::create_directories(KEY_DIR);

    if (std::filesystem::exists(PRIVATE_KEY_FILE) &&
        std::filesystem::exists(PUBLIC_KEY_FILE)) {
        logInfo("key pair already exists, skip generation");
        return;
    }

    logInfo("generating RSA key pair because one or both key files are missing");
    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, nullptr);
    if (!ctx) {
        throw std::runtime_error("EVP_PKEY_CTX_new_id failed");
    }

    if (EVP_PKEY_keygen_init(ctx) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("EVP_PKEY_keygen_init failed");
    }

    if (EVP_PKEY_CTX_set_rsa_keygen_bits(ctx, 2048) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("Set RSA bits failed");
    }

    EVP_PKEY* key = nullptr;
    if (EVP_PKEY_keygen(ctx, &key) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("RSA key generation failed");
    }

    writeTextFile(PRIVATE_KEY_FILE, privateKeyToPem(key));
    writeTextFile(PUBLIC_KEY_FILE, publicKeyToPem(key));

    EVP_PKEY_free(key);
    EVP_PKEY_CTX_free(ctx);
    logInfo("generated and persisted missing RSA key pair");
}

static std::vector<unsigned char> randomBytes(size_t size) {
    logInfo("generating random bytes size=" + std::to_string(size));
    std::vector<unsigned char> data(size);
    if (RAND_bytes(data.data(), static_cast<int>(data.size())) != 1) {
        throw std::runtime_error("RAND_bytes failed");
    }
    logInfo("generated random bytes size=" + std::to_string(size));
    return data;
}

static std::string signData(const std::string& data) {
    logInfo("signing data bytes=" + std::to_string(data.size()));
    EVP_PKEY* key = loadPrivateKey();

    EVP_MD_CTX* mdctx = EVP_MD_CTX_new();
    EVP_PKEY_CTX* pctx = nullptr;

    if (EVP_DigestSignInit(mdctx, &pctx, EVP_sha256(), nullptr, key) <= 0) {
        EVP_MD_CTX_free(mdctx);
        EVP_PKEY_free(key);
        throw std::runtime_error("DigestSignInit failed");
    }

    EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING);
    EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1);

    EVP_DigestSignUpdate(mdctx, data.data(), data.size());

    size_t sigLen = 0;
    EVP_DigestSignFinal(mdctx, nullptr, &sigLen);

    std::vector<unsigned char> signature(sigLen);
    if (EVP_DigestSignFinal(mdctx, signature.data(), &sigLen) <= 0) {
        EVP_MD_CTX_free(mdctx);
        EVP_PKEY_free(key);
        throw std::runtime_error("DigestSignFinal failed");
    }

    signature.resize(sigLen);

    EVP_MD_CTX_free(mdctx);
    EVP_PKEY_free(key);

    std::string encoded = base64Encode(signature);
    logInfo(
        "signed data bytes=" + std::to_string(data.size()) +
        " signature_base64_bytes=" + std::to_string(encoded.size())
    );
    return encoded;
}

static bool verifySignature(
    const std::string& publicKeyPem,
    const std::string& data,
    const std::string& signatureBase64
) {
    logInfo(
        "verifying signature data_bytes=" + std::to_string(data.size()) +
        " signature_base64_bytes=" + std::to_string(signatureBase64.size())
    );
    EVP_PKEY* key = publicKeyFromPem(publicKeyPem);
    std::vector<unsigned char> signature = base64Decode(signatureBase64);

    EVP_MD_CTX* mdctx = EVP_MD_CTX_new();
    EVP_PKEY_CTX* pctx = nullptr;

    if (EVP_DigestVerifyInit(mdctx, &pctx, EVP_sha256(), nullptr, key) <= 0) {
        EVP_MD_CTX_free(mdctx);
        EVP_PKEY_free(key);
        throw std::runtime_error("DigestVerifyInit failed");
    }

    EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING);
    EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1);

    EVP_DigestVerifyUpdate(mdctx, data.data(), data.size());

    int ok = EVP_DigestVerifyFinal(
        mdctx,
        signature.data(),
        signature.size()
    );

    EVP_MD_CTX_free(mdctx);
    EVP_PKEY_free(key);

    logInfo(std::string("signature verification result valid=") + (ok == 1 ? "true" : "false"));
    return ok == 1;
}

static std::string rsaEncrypt(
    const std::string& publicKeyPem,
    const std::string& plainBase64
) {
    logInfo("RSA encrypt request plain_base64_bytes=" + std::to_string(plainBase64.size()));
    EVP_PKEY* key = publicKeyFromPem(publicKeyPem);
    std::vector<unsigned char> plain = base64Decode(plainBase64);

    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(key, nullptr);
    EVP_PKEY_encrypt_init(ctx);
    EVP_PKEY_CTX_set_rsa_padding(ctx, RSA_PKCS1_OAEP_PADDING);
    EVP_PKEY_CTX_set_rsa_oaep_md(ctx, EVP_sha256());
    EVP_PKEY_CTX_set_rsa_mgf1_md(ctx, EVP_sha256());

    size_t outLen = 0;
    EVP_PKEY_encrypt(ctx, nullptr, &outLen, plain.data(), plain.size());

    std::vector<unsigned char> cipher(outLen);
    if (EVP_PKEY_encrypt(ctx, cipher.data(), &outLen, plain.data(), plain.size()) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        EVP_PKEY_free(key);
        throw std::runtime_error("RSA encrypt failed");
    }

    cipher.resize(outLen);

    EVP_PKEY_CTX_free(ctx);
    EVP_PKEY_free(key);

    std::string encoded = base64Encode(cipher);
    logInfo(
        "RSA encrypt completed plain_bytes=" + std::to_string(plain.size()) +
        " cipher_base64_bytes=" + std::to_string(encoded.size())
    );
    return encoded;
}

static std::string rsaDecrypt(const std::string& cipherBase64) {
    logInfo("RSA decrypt request cipher_base64_bytes=" + std::to_string(cipherBase64.size()));
    EVP_PKEY* key = loadPrivateKey();
    std::vector<unsigned char> cipher = base64Decode(cipherBase64);

    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new(key, nullptr);
    EVP_PKEY_decrypt_init(ctx);
    EVP_PKEY_CTX_set_rsa_padding(ctx, RSA_PKCS1_OAEP_PADDING);
    EVP_PKEY_CTX_set_rsa_oaep_md(ctx, EVP_sha256());
    EVP_PKEY_CTX_set_rsa_mgf1_md(ctx, EVP_sha256());

    size_t outLen = 0;
    EVP_PKEY_decrypt(ctx, nullptr, &outLen, cipher.data(), cipher.size());

    std::vector<unsigned char> plain(outLen);
    if (EVP_PKEY_decrypt(ctx, plain.data(), &outLen, cipher.data(), cipher.size()) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        EVP_PKEY_free(key);
        throw std::runtime_error("RSA decrypt failed");
    }

    plain.resize(outLen);

    EVP_PKEY_CTX_free(ctx);
    EVP_PKEY_free(key);

    std::string encoded = base64Encode(plain);
    logInfo(
        "RSA decrypt completed cipher_bytes=" + std::to_string(cipher.size()) +
        " plain_base64_bytes=" + std::to_string(encoded.size())
    );
    return encoded;
}

static json aesGcmEncrypt(const std::string& keyBase64, const std::string& plainBase64) {
    logInfo(
        "AES-GCM encrypt request key_base64_bytes=" + std::to_string(keyBase64.size()) +
        " plain_base64_bytes=" + std::to_string(plainBase64.size())
    );
    std::vector<unsigned char> key = base64Decode(keyBase64);
    std::vector<unsigned char> plain = base64Decode(plainBase64);

    if (key.size() != 32) {
        throw std::runtime_error("AES-256 key must be 32 bytes");
    }

    std::vector<unsigned char> nonce = randomBytes(12);
    std::vector<unsigned char> ciphertext(plain.size());
    std::vector<unsigned char> tag(16);

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();

    EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr);
    EVP_EncryptInit_ex(ctx, nullptr, nullptr, key.data(), nonce.data());

    int len = 0;
    int ciphertextLen = 0;

    EVP_EncryptUpdate(
        ctx,
        ciphertext.data(),
        &len,
        plain.data(),
        static_cast<int>(plain.size())
    );
    ciphertextLen = len;

    EVP_EncryptFinal_ex(ctx, ciphertext.data() + len, &len);
    ciphertextLen += len;

    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, 16, tag.data());
    EVP_CIPHER_CTX_free(ctx);

    ciphertext.resize(ciphertextLen);

    logInfo(
        "AES-GCM encrypt completed plain_bytes=" + std::to_string(plain.size()) +
        " ciphertext_bytes=" + std::to_string(ciphertext.size()) +
        " nonce_bytes=" + std::to_string(nonce.size()) +
        " tag_bytes=" + std::to_string(tag.size())
    );

    return {
        {"nonce", base64Encode(nonce)},
        {"ciphertext", base64Encode(ciphertext)},
        {"tag", base64Encode(tag)}
    };
}

static std::string aesGcmDecrypt(
    const std::string& keyBase64,
    const std::string& nonceBase64,
    const std::string& ciphertextBase64,
    const std::string& tagBase64
) {
    logInfo(
        "AES-GCM decrypt request key_base64_bytes=" + std::to_string(keyBase64.size()) +
        " nonce_base64_bytes=" + std::to_string(nonceBase64.size()) +
        " ciphertext_base64_bytes=" + std::to_string(ciphertextBase64.size()) +
        " tag_base64_bytes=" + std::to_string(tagBase64.size())
    );
    std::vector<unsigned char> key = base64Decode(keyBase64);
    std::vector<unsigned char> nonce = base64Decode(nonceBase64);
    std::vector<unsigned char> ciphertext = base64Decode(ciphertextBase64);
    std::vector<unsigned char> tag = base64Decode(tagBase64);

    if (key.size() != 32) {
        throw std::runtime_error("AES-256 key must be 32 bytes");
    }

    std::vector<unsigned char> plain(ciphertext.size());

    EVP_CIPHER_CTX* ctx = EVP_CIPHER_CTX_new();

    EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr);
    EVP_DecryptInit_ex(ctx, nullptr, nullptr, key.data(), nonce.data());

    int len = 0;
    int plainLen = 0;

    EVP_DecryptUpdate(
        ctx,
        plain.data(),
        &len,
        ciphertext.data(),
        static_cast<int>(ciphertext.size())
    );
    plainLen = len;

    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16, tag.data());

    int ok = EVP_DecryptFinal_ex(ctx, plain.data() + len, &len);
    EVP_CIPHER_CTX_free(ctx);

    if (ok <= 0) {
        throw std::runtime_error("AES-GCM tag verification failed");
    }

    plainLen += len;
    plain.resize(plainLen);

    std::string encoded = base64Encode(plain);
    logInfo(
        "AES-GCM decrypt completed ciphertext_bytes=" + std::to_string(ciphertext.size()) +
        " plain_base64_bytes=" + std::to_string(encoded.size())
    );
    return encoded;
}

static std::string fingerprint(const std::string& publicKeyPem) {
    logInfo("calculating public key fingerprint public_key_bytes=" + std::to_string(publicKeyPem.size()));
    unsigned char hash[SHA256_DIGEST_LENGTH];

    SHA256(
        reinterpret_cast<const unsigned char*>(publicKeyPem.data()),
        publicKeyPem.size(),
        hash
    );

    static const char* hex = "0123456789abcdef";
    std::string result;
    result.reserve(SHA256_DIGEST_LENGTH * 2);

    for (unsigned char b : hash) {
        result.push_back(hex[b >> 4]);
        result.push_back(hex[b & 0x0f]);
    }

    logInfo("calculated public key fingerprint value=" + result);
    return result;
}

static json parseBody(const httplib::Request& req) {
    if (req.body.empty()) {
        logInfo("request has empty JSON body path=" + req.path);
        return json::object();
    }
    logInfo("parsing JSON body path=" + req.path + " bytes=" + std::to_string(req.body.size()));
    return json::parse(req.body);
}

static void jsonResponse(httplib::Response& res, const json& body) {
    std::string responseBody = body.dump();
    std::string statusText = res.status > 0 ? std::to_string(res.status) : "auto";
    logInfo(
        "sending JSON response status=" + statusText +
        " bytes=" + std::to_string(responseBody.size())
    );
    res.set_content(responseBody, "application/json");
}

static void errorResponse(httplib::Response& res, const std::exception& e) {
    logError(std::string("request failed error=\"") + e.what() + "\"");
    res.status = 500;
    res.set_content(json({{"error", e.what()}}).dump(), "application/json");
}

static void generateAndPersistKeyPair() {
    logInfo("generating RSA key pair key_dir=" + KEY_DIR);
    std::filesystem::create_directories(KEY_DIR);

    EVP_PKEY_CTX* ctx = EVP_PKEY_CTX_new_id(EVP_PKEY_RSA, nullptr);
    if (!ctx) {
        throw std::runtime_error("EVP_PKEY_CTX_new_id failed");
    }

    if (EVP_PKEY_keygen_init(ctx) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("EVP_PKEY_keygen_init failed");
    }

    if (EVP_PKEY_CTX_set_rsa_keygen_bits(ctx, 2048) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("Set RSA bits failed");
    }

    EVP_PKEY* key = nullptr;
    if (EVP_PKEY_keygen(ctx, &key) <= 0) {
        EVP_PKEY_CTX_free(ctx);
        throw std::runtime_error("RSA key generation failed");
    }

    writeTextFile(PRIVATE_KEY_FILE, privateKeyToPem(key));
    writeTextFile(PUBLIC_KEY_FILE, publicKeyToPem(key));

    EVP_PKEY_free(key);
    EVP_PKEY_CTX_free(ctx);
    logInfo("generated RSA key pair private_key_file=" + PRIVATE_KEY_FILE + " public_key_file=" + PUBLIC_KEY_FILE);
}


int main(int argc, char* argv[]) {
    ServiceConfig config;

    try {
        logInfo("crypto service starting");
        config = parseArgs(argc, argv);
        setKeyDir(config.keyDir);
        OpenSSL_add_all_algorithms();
        logInfo("OpenSSL algorithms initialized");
        std::filesystem::create_directories(KEY_DIR);
        logInfo(
            "startup configuration host=" + config.host +
            " port=" + std::to_string(config.port) +
            " key_dir=" + KEY_DIR
        );
    } catch (const std::exception& e) {
        logError(std::string("startup failed error=\"") + e.what() + "\"");
        return 1;
    }

    httplib::Server server;

    server.set_logger([](const httplib::Request& req, const httplib::Response& res) {
        std::string remote = req.remote_addr.empty() ? "-" : req.remote_addr;
        logInfo(
            "http completed method=" + req.method +
            " path=" + req.path +
            " remote=" + remote +
            " status=" + std::to_string(res.status) +
            " request_body_bytes=" + std::to_string(req.body.size()) +
            " response_body_bytes=" + std::to_string(res.body.size())
        );
    });

    server.Get("/health", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("health", req);
        jsonResponse(res, {
            {"status", "ok"},
            {"keyDir", KEY_DIR}
        });
    });

    server.Get("/key/public", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.public", req);
        try {
            jsonResponse(res, {{"publicKey", readTextFile(PUBLIC_KEY_FILE)}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Get("/key/private", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.private", req);
        try {
            jsonResponse(res, {{"privateKey", readTextFile(PRIVATE_KEY_FILE)}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Get("/key/status", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.status", req);
        try {
            bool hasPrivate = std::filesystem::exists(PRIVATE_KEY_FILE);
            bool hasPublic = std::filesystem::exists(PUBLIC_KEY_FILE);
            logInfo(
                std::string("key status checked has_private=") + (hasPrivate ? "true" : "false") +
                " has_public=" + (hasPublic ? "true" : "false")
            );

            jsonResponse(res, {
                {"hasPrivateKey", hasPrivate ? "true" : "false"},
                {"hasPublicKey", hasPublic ? "true" : "false"}
            });
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });
    
    server.Post("/key/generate", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.generate", req);
        try {
            if (std::filesystem::exists(PRIVATE_KEY_FILE) ||
                std::filesystem::exists(PUBLIC_KEY_FILE)) {
                logWarn("key generation rejected because key pair already exists");
                res.status = 409;
                res.set_content(
                    json({
                        {"success", "false"},
                        {"error", "Key pair already exists"}
                    }).dump(),
                    "application/json"
                );
                return;
            }

            generateAndPersistKeyPair();

            jsonResponse(res, {
                {"success", "true"},
                {"privateKey", readTextFile(PRIVATE_KEY_FILE)},
                {"publicKey", readTextFile(PUBLIC_KEY_FILE)}
            });
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });
    
    server.Post("/key/delete", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.delete", req);
        try {
            bool deletedPrivate = false;
            bool deletedPublic = false;

            if (std::filesystem::exists(PRIVATE_KEY_FILE)) {
                deletedPrivate = std::filesystem::remove(PRIVATE_KEY_FILE);
            }

            if (std::filesystem::exists(PUBLIC_KEY_FILE)) {
                deletedPublic = std::filesystem::remove(PUBLIC_KEY_FILE);
            }

            logInfo(
                std::string("key delete completed deleted_private=") + (deletedPrivate ? "true" : "false") +
                " deleted_public=" + (deletedPublic ? "true" : "false")
            );

            jsonResponse(res, {
                {"success", "true"},
                {"deletedPrivateKey", deletedPrivate ? "true" : "false"},
                {"deletedPublicKey", deletedPublic ? "true" : "false"}
            });
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });



    server.Post("/sign", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("sign", req);
        try {
            json body = parseBody(req);
            std::string data = body.at("data").get<std::string>();

            jsonResponse(res, {{"signature", signData(data)}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/verify", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("verify", req);
        try {
            json body = parseBody(req);

            bool valid = verifySignature(
                body.at("publicKey").get<std::string>(),
                body.at("data").get<std::string>(),
                body.at("signature").get<std::string>()
            );

            jsonResponse(res, {{"valid", valid ? "true" : "false"}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/aes/generate", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("aes.generate", req);
        try {
            jsonResponse(res, {{"key", base64Encode(randomBytes(32))}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/rsa/encrypt", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("rsa.encrypt", req);
        try {
            json body = parseBody(req);

            std::string cipher = rsaEncrypt(
                body.at("publicKey").get<std::string>(),
                body.at("plain").get<std::string>()
            );

            jsonResponse(res, {{"cipher", cipher}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/rsa/decrypt", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("rsa.decrypt", req);
        try {
            json body = parseBody(req);
            std::string plain = rsaDecrypt(body.at("cipher").get<std::string>());

            jsonResponse(res, {{"plain", plain}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/aes-gcm/encrypt", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("aes-gcm.encrypt", req);
        try {
            json body = parseBody(req);

            json result = aesGcmEncrypt(
                body.at("key").get<std::string>(),
                body.at("plain").get<std::string>()
            );

            jsonResponse(res, result);
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/aes-gcm/decrypt", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("aes-gcm.decrypt", req);
        try {
            json body = parseBody(req);

            std::string plain = aesGcmDecrypt(
                body.at("key").get<std::string>(),
                body.at("nonce").get<std::string>(),
                body.at("ciphertext").get<std::string>(),
                body.at("tag").get<std::string>()
            );

            jsonResponse(res, {{"plain", plain}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/key/fingerprint", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.fingerprint", req);
        try {
            json body = parseBody(req);
            std::string publicKey = body.at("publicKey").get<std::string>();

            jsonResponse(res, {{"fingerprint", fingerprint(publicKey)}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/key/import-text", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.import-text", req);
        try {
            json body = parseBody(req);
            std::string privateKeyPem = body.at("privateKey").get<std::string>();
            logInfo("importing private key from request text bytes=" + std::to_string(privateKeyPem.size()));

            BIO* bio = BIO_new_mem_buf(privateKeyPem.data(), static_cast<int>(privateKeyPem.size()));
            EVP_PKEY* key = PEM_read_bio_PrivateKey(bio, nullptr, nullptr, nullptr);
            BIO_free(bio);

            if (!key) {
                throw std::runtime_error("Invalid private key PEM");
            }

            writeTextFile(PRIVATE_KEY_FILE, privateKeyPem);
            writeTextFile(PUBLIC_KEY_FILE, publicKeyToPem(key));

            EVP_PKEY_free(key);

            logInfo("imported private key from text and regenerated public key");
            jsonResponse(res, {{"success", "true"}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    server.Post("/key/import-file", [](const httplib::Request& req, httplib::Response& res) {
        logRequest("key.import-file", req);
        try {
            json body = parseBody(req);
            std::string path = body.at("path").get<std::string>();
            logInfo("importing private key from file path=" + path);
            std::string privateKeyPem = readTextFile(path);

            BIO* bio = BIO_new_mem_buf(privateKeyPem.data(), static_cast<int>(privateKeyPem.size()));
            EVP_PKEY* key = PEM_read_bio_PrivateKey(bio, nullptr, nullptr, nullptr);
            BIO_free(bio);

            if (!key) {
                throw std::runtime_error("Invalid private key PEM file");
            }

            writeTextFile(PRIVATE_KEY_FILE, privateKeyPem);
            writeTextFile(PUBLIC_KEY_FILE, publicKeyToPem(key));

            EVP_PKEY_free(key);

            logInfo("imported private key from file and regenerated public key path=" + path);
            jsonResponse(res, {{"success", "true"}});
        } catch (const std::exception& e) {
            errorResponse(res, e);
        }
    });

    logInfo(
        "crypto service listening url=http://" + config.host + ":" + std::to_string(config.port) +
        " key_dir=" + KEY_DIR
    );
    if (!server.listen(config.host, config.port)) {
        logError("failed to listen host=" + config.host + " port=" + std::to_string(config.port));
        return 1;
    }

    return 0;
}
