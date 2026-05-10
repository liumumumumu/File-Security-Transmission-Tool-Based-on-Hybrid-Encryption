"""
加密模块 HTTP 服务

将所有加密函数包装为 HTTP API，供 Java TCP 模块通过网络调用。
所有二进制数据（密钥、密文、签名等）统一用 Base64 编码在 JSON 中传输。
RSA 公钥/私钥使用 PEM 文本格式传输。

启动: python crypto_service.py
默认: http://0.0.0.0:5000

Java 端配置:
  crypto-service.address: 127.0.0.1
  crypto-service.port: 5000
"""
import base64
from flask import Flask, request, jsonify

from crypto.aes_gcm import generate_aes_key, encrypt_block, decrypt_block
from key_exchange.rsa_utils import (
    generate_private_key,
    generate_public_key,
    export_private_key,
    export_public_key,
    load_private_key,
    load_public_key,
    public_key_fingerprint,
    sign,
    verify_signature,
    encrypt_aes_key,
    decrypt_aes_key,
)

app = Flask(__name__)


# ── 工具函数：Base64 编解码 ───────────────────────────────

def b64e(data: bytes) -> str:
    return base64.b64encode(data).decode('ascii')


def b64d(s: str) -> bytes:
    return base64.b64decode(s)


# ── 健康检查 ──────────────────────────────────────────────

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "service": "crypto-service"})


# ── RSA 密钥对生成 ────────────────────────────────────────

@app.route('/generate-keypair', methods=['POST'])
def api_generate_keypair():
    """
    生成 RSA-2048 密钥对

    请求: {}（无参数）
    响应: {
        "privateKey": "-----BEGIN PRIVATE KEY-----\n...",
        "publicKey":  "-----BEGIN PUBLIC KEY-----\n...",
        "fingerprint": "a3b2c1..."
    }
    """
    priv = generate_private_key()
    pub = generate_public_key(priv)
    return jsonify({
        "privateKey": export_private_key(priv).decode('ascii'),
        "publicKey": export_public_key(pub).decode('ascii'),
        "fingerprint": public_key_fingerprint(pub),
    })


# ── 公钥指纹 ──────────────────────────────────────────────

@app.route('/public-key-fingerprint', methods=['POST'])
def api_public_key_fingerprint():
    """
    计算公钥的 SHA-256 指纹（用作账号 ID）

    请求: { "publicKey": "-----BEGIN PUBLIC KEY-----\n..." }
    响应: { "fingerprint": "a3b2c1d4..." }
    """
    data = request.get_json()
    pub = load_public_key(data['publicKey'].encode('ascii'))
    return jsonify({
        "fingerprint": public_key_fingerprint(pub),
    })


# ── 数字签名 ──────────────────────────────────────────────

@app.route('/sign', methods=['POST'])
def api_sign():
    """
    用 RSA 私钥对 challenge 签名

    请求: {
        "challenge":  "base64编码的challenge",
        "privateKey": "-----BEGIN PRIVATE KEY-----\n..."
    }
    响应: { "signature": "base64编码的签名" }
    """
    data = request.get_json()
    challenge = b64d(data['challenge'])
    priv = load_private_key(data['privateKey'].encode('ascii'))
    sig = sign(challenge, priv)
    return jsonify({
        "signature": b64e(sig),
    })


@app.route('/verify-signature', methods=['POST'])
def api_verify_signature():
    """
    用 RSA 公钥验证签名

    请求: {
        "publicKey":  "-----BEGIN PUBLIC KEY-----\n...",
        "challenge":  "base64编码的challenge",
        "signature":  "base64编码的签名"
    }
    响应: { "valid": true/false }
    """
    data = request.get_json()
    pub = load_public_key(data['publicKey'].encode('ascii'))
    challenge = b64d(data['challenge'])
    sig = b64d(data['signature'])
    ok = verify_signature(pub, challenge, sig)
    return jsonify({
        "valid": ok,
    })


# ── AES 会话密钥生成 ──────────────────────────────────────

@app.route('/generate-aes-key', methods=['POST'])
def api_generate_aes_key():
    """
    生成 AES-256 会话密钥（32 字节随机数）

    请求: {}
    响应: { "aesKey": "base64编码的32字节密钥" }
    """
    key = generate_aes_key()
    return jsonify({
        "aesKey": b64e(key),
    })


# ── AES 密钥的 RSA 加密/解密（密钥协商）──────────────────

@app.route('/encrypt-aes-key', methods=['POST'])
def api_encrypt_aes_key():
    """
    用 RSA 公钥加密 AES 会话密钥

    请求: {
        "aesKey":    "base64编码的AES密钥",
        "publicKey": "-----BEGIN PUBLIC KEY-----\n..."
    }
    响应: { "encryptedAesKey": "base64编码的加密结果" }
    """
    data = request.get_json()
    aes_key = b64d(data['aesKey'])
    pub = load_public_key(data['publicKey'].encode('ascii'))
    encrypted = encrypt_aes_key(aes_key, pub)
    return jsonify({
        "encryptedAesKey": b64e(encrypted),
    })


@app.route('/decrypt-aes-key', methods=['POST'])
def api_decrypt_aes_key():
    """
    用 RSA 私钥解密 AES 会话密钥

    请求: {
        "encryptedAesKey": "base64编码的加密AES密钥",
        "privateKey":      "-----BEGIN PRIVATE KEY-----\n..."
    }
    响应: { "aesKey": "base64编码的AES密钥" }
    """
    data = request.get_json()
    encrypted = b64d(data['encryptedAesKey'])
    priv = load_private_key(data['privateKey'].encode('ascii'))
    aes_key = decrypt_aes_key(encrypted, priv)
    return jsonify({
        "aesKey": b64e(aes_key),
    })


# ── AES-GCM 数据块加密/解密 ──────────────────────────────

@app.route('/encrypt-block', methods=['POST'])
def api_encrypt_block():
    """
    用 AES-256-GCM 加密一个数据块

    请求: {
        "data":   "base64编码的明文数据",
        "aesKey": "base64编码的AES密钥"
    }
    响应: {
        "nonce":      "base64编码的12字节nonce",
        "ciphertext": "base64编码的密文",
        "tag":        "base64编码的16字节tag"
    }
    """
    data = request.get_json()
    plaintext = b64d(data['data'])
    key = b64d(data['aesKey'])
    block = encrypt_block(plaintext, key)
    return jsonify({
        "nonce": b64e(block.nonce),
        "ciphertext": b64e(block.ciphertext),
        "tag": b64e(block.tag),
    })


@app.route('/decrypt-block', methods=['POST'])
def api_decrypt_block():
    """
    用 AES-256-GCM 解密一个数据块

    请求: {
        "ciphertext": "base64编码的密文",
        "nonce":      "base64编码的nonce",
        "tag":        "base64编码的tag",
        "aesKey":     "base64编码的AES密钥"
    }
    响应: { "data": "base64编码的明文" }
    错误: 数据被篡改时返回 400 + {"error": "InvalidTag: ..."}
    """
    data = request.get_json()
    ciphertext = b64d(data['ciphertext'])
    nonce = b64d(data['nonce'])
    tag = b64d(data['tag'])
    key = b64d(data['aesKey'])
    try:
        plaintext = decrypt_block(ciphertext, nonce, tag, key)
        return jsonify({
            "data": b64e(plaintext),
        })
    except Exception as e:
        return jsonify({"error": f"{type(e).__name__}: {e}"}), 400


# ── 错误处理 ──────────────────────────────────────────────

@app.errorhandler(Exception)
def handle_error(e):
    return jsonify({"error": f"{type(e).__name__}: {e}"}), 500


# ── 启动 ──────────────────────────────────────────────────

if __name__ == '__main__':
    print("=" * 50)
    print("加密模块 HTTP 服务")
    print("=" * 50)
    print("监听: http://0.0.0.0:5000")
    print()
    print("可用接口:")
    print("  GET  /health                  - 健康检查")
    print("  POST /generate-keypair        - 生成 RSA 密钥对")
    print("  POST /public-key-fingerprint  - 计算公钥指纹")
    print("  POST /sign                    - 数字签名")
    print("  POST /verify-signature        - 验证签名")
    print("  POST /generate-aes-key        - 生成 AES 密钥")
    print("  POST /encrypt-aes-key         - RSA 加密 AES 密钥")
    print("  POST /decrypt-aes-key         - RSA 解密 AES 密钥")
    print("  POST /encrypt-block           - AES-GCM 加密")
    print("  POST /decrypt-block           - AES-GCM 解密")
    print()
    app.run(host='0.0.0.0', port=5000, debug=True)
