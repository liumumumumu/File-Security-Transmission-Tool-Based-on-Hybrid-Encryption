"""
加密服务 HTTP API 接口测试
运行前先启动服务: python crypto_service.py
"""
import base64
import requests

BASE = "http://127.0.0.1:5000"


def b64e(data: bytes) -> str:
    return base64.b64encode(data).decode()


def b64d(s: str) -> bytes:
    return base64.b64decode(s)


def test_all():
    print("=" * 50)
    print("加密服务 HTTP 接口测试")
    print("=" * 50)

    # 1. 健康检查
    r = requests.get(f"{BASE}/health")
    assert r.status_code == 200
    print("[1] GET  /health               OK")

    # 2. 生成密钥对
    r = requests.post(f"{BASE}/generate-keypair", json={})
    assert r.status_code == 200
    keypair = r.json()
    priv_pem = keypair['privateKey']
    pub_pem = keypair['publicKey']
    fingerprint = keypair['fingerprint']
    assert priv_pem.startswith("-----BEGIN PRIVATE KEY-----")
    assert pub_pem.startswith("-----BEGIN PUBLIC KEY-----")
    assert len(fingerprint) == 64
    print(f"[2] POST /generate-keypair     OK  fingerprint={fingerprint[:16]}...")

    # 3. 公钥指纹
    r = requests.post(f"{BASE}/public-key-fingerprint", json={"publicKey": pub_pem})
    assert r.status_code == 200
    assert r.json()['fingerprint'] == fingerprint
    print("[3] POST /public-key-fingerprint OK")

    # 4. 签名
    import os
    challenge = os.urandom(32)
    r = requests.post(f"{BASE}/sign", json={
        "challenge": b64e(challenge),
        "privateKey": priv_pem,
    })
    assert r.status_code == 200
    signature = r.json()['signature']
    print(f"[4] POST /sign                 OK  sig_len={len(b64d(signature))}")

    # 5. 验签（正确）
    r = requests.post(f"{BASE}/verify-signature", json={
        "publicKey": pub_pem,
        "challenge": b64e(challenge),
        "signature": signature,
    })
    assert r.status_code == 200
    assert r.json()['valid'] is True
    print("[5] POST /verify-signature     OK  valid=True")

    # 6. 验签（篡改 challenge）
    tampered = bytearray(challenge)
    tampered[0] ^= 0xFF
    r = requests.post(f"{BASE}/verify-signature", json={
        "publicKey": pub_pem,
        "challenge": b64e(bytes(tampered)),
        "signature": signature,
    })
    assert r.json()['valid'] is False
    print("[6] POST /verify-signature     OK  tampered → valid=False")

    # 7. 生成 AES 密钥
    r = requests.post(f"{BASE}/generate-aes-key", json={})
    assert r.status_code == 200
    aes_key = r.json()['aesKey']
    assert len(b64d(aes_key)) == 32
    print(f"[7] POST /generate-aes-key     OK  key_len={len(b64d(aes_key))}")

    # 8. RSA 加密 AES 密钥
    r = requests.post(f"{BASE}/encrypt-aes-key", json={
        "aesKey": aes_key,
        "publicKey": pub_pem,
    })
    assert r.status_code == 200
    enc_aes_key = r.json()['encryptedAesKey']
    print(f"[8] POST /encrypt-aes-key      OK  enc_len={len(b64d(enc_aes_key))}")

    # 9. RSA 解密 AES 密钥
    r = requests.post(f"{BASE}/decrypt-aes-key", json={
        "encryptedAesKey": enc_aes_key,
        "privateKey": priv_pem,
    })
    assert r.status_code == 200
    recovered_key = r.json()['aesKey']
    assert recovered_key == aes_key
    print("[9] POST /decrypt-aes-key      OK  key matches!")

    # 10. AES-GCM 加密
    plaintext = b"Hello, this is a secret message for testing!" * 10
    r = requests.post(f"{BASE}/encrypt-block", json={
        "data": b64e(plaintext),
        "aesKey": aes_key,
    })
    assert r.status_code == 200
    enc = r.json()
    assert len(b64d(enc['nonce'])) == 12
    assert len(b64d(enc['tag'])) == 16
    print(f"[10] POST /encrypt-block       OK  ct_len={len(b64d(enc['ciphertext']))}")

    # 11. AES-GCM 解密
    r = requests.post(f"{BASE}/decrypt-block", json={
        "ciphertext": enc['ciphertext'],
        "nonce": enc['nonce'],
        "tag": enc['tag'],
        "aesKey": aes_key,
    })
    assert r.status_code == 200
    recovered = b64d(r.json()['data'])
    assert recovered == plaintext
    print("[11] POST /decrypt-block       OK  plaintext matches!")

    # 12. 篡改检测
    tampered_ct = bytearray(b64d(enc['ciphertext']))
    tampered_ct[0] ^= 0xFF
    r = requests.post(f"{BASE}/decrypt-block", json={
        "ciphertext": b64e(bytes(tampered_ct)),
        "nonce": enc['nonce'],
        "tag": enc['tag'],
        "aesKey": aes_key,
    })
    assert r.status_code == 400
    assert "InvalidTag" in r.json()['error']
    print("[12] POST /decrypt-block       OK  tampered → 400 InvalidTag")

    print()
    print("=" * 50)
    print("全部 12 个接口测试通过!")
    print("=" * 50)


if __name__ == '__main__':
    test_all()
