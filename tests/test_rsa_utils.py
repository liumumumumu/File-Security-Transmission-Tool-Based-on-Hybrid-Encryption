"""
RSA 密钥协商模块单元测试
运行: python -m pytest tests/test_rsa_utils.py -v
或:  python tests/test_rsa_utils.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from key_exchange.rsa_utils import (
    RSA_KEY_SIZE,
    generate_private_key,
    generate_public_key,
    export_private_key,
    export_public_key,
    load_private_key,
    load_public_key,
    sign,
    verify_signature,
    encrypt_aes_key,
    decrypt_aes_key,
)


class TestKeyGeneration(unittest.TestCase):
    def setUp(self):
        self.priv = generate_private_key()
        self.pub = generate_public_key(self.priv)

    def test_private_key_size(self):
        self.assertEqual(self.priv.key_size, RSA_KEY_SIZE)

    def test_public_key_size(self):
        self.assertEqual(self.pub.key_size, RSA_KEY_SIZE)

    def test_different_keys_each_call(self):
        priv2 = generate_private_key()
        pub2 = generate_public_key(priv2)
        self.assertNotEqual(
            export_private_key(self.priv),
            export_private_key(priv2),
        )
        self.assertNotEqual(
            export_public_key(self.pub),
            export_public_key(pub2),
        )


class TestKeySerialize(unittest.TestCase):
    def setUp(self):
        self.priv = generate_private_key()
        self.pub = generate_public_key(self.priv)

    def test_private_key_pem_roundtrip(self):
        pem = export_private_key(self.priv)
        self.assertTrue(pem.startswith(b"-----BEGIN PRIVATE KEY-----"))
        loaded = load_private_key(pem)
        self.assertEqual(export_private_key(loaded), pem)

    def test_public_key_pem_roundtrip(self):
        pem = export_public_key(self.pub)
        self.assertTrue(pem.startswith(b"-----BEGIN PUBLIC KEY-----"))
        loaded = load_public_key(pem)
        self.assertEqual(export_public_key(loaded), pem)


class TestSign(unittest.TestCase):
    def setUp(self):
        self.priv = generate_private_key()
        self.pub = generate_public_key(self.priv)

    def test_sign_returns_bytes(self):
        sig = sign(b"challenge", self.priv)
        self.assertIsInstance(sig, bytes)

    def test_signature_length(self):
        sig = sign(b"challenge", self.priv)
        self.assertEqual(len(sig), RSA_KEY_SIZE // 8)

    def test_verify_valid_signature(self):
        challenge = os.urandom(32)
        sig = sign(challenge, self.priv)
        self.assertTrue(verify_signature(self.pub, challenge, sig))

    def test_verify_wrong_public_key(self):
        challenge = os.urandom(32)
        sig = sign(challenge, self.priv)
        other_priv = generate_private_key()
        other_pub = generate_public_key(other_priv)
        self.assertFalse(verify_signature(other_pub, challenge, sig))

    def test_verify_tampered_challenge(self):
        challenge = os.urandom(32)
        sig = sign(challenge, self.priv)
        tampered = bytearray(challenge)
        tampered[0] ^= 0xFF
        self.assertFalse(verify_signature(self.pub, bytes(tampered), sig))

    def test_verify_tampered_signature(self):
        challenge = os.urandom(32)
        sig = sign(challenge, self.priv)
        tampered = bytearray(sig)
        tampered[0] ^= 0xFF
        self.assertFalse(verify_signature(self.pub, challenge, bytes(tampered)))

    def test_sign_empty_data(self):
        sig = sign(b"", self.priv)
        self.assertTrue(verify_signature(self.pub, b"", sig))

    def test_sign_large_data(self):
        data = os.urandom(10000)
        sig = sign(data, self.priv)
        self.assertTrue(verify_signature(self.pub, data, sig))


class TestAesKeyEncryption(unittest.TestCase):
    def setUp(self):
        self.priv = generate_private_key()
        self.pub = generate_public_key(self.priv)
        self.aes_key = os.urandom(32)

    def test_encrypt_decrypt_roundtrip(self):
        encrypted = encrypt_aes_key(self.aes_key, self.pub)
        decrypted = decrypt_aes_key(encrypted, self.priv)
        self.assertEqual(decrypted, self.aes_key)

    def test_encrypted_length(self):
        encrypted = encrypt_aes_key(self.aes_key, self.pub)
        self.assertEqual(len(encrypted), RSA_KEY_SIZE // 8)

    def test_different_ciphertext_each_time(self):
        """OAEP 填充包含随机性，同一明文每次加密结果不同"""
        enc1 = encrypt_aes_key(self.aes_key, self.pub)
        enc2 = encrypt_aes_key(self.aes_key, self.pub)
        self.assertNotEqual(enc1, enc2)

    def test_wrong_private_key_fails(self):
        encrypted = encrypt_aes_key(self.aes_key, self.pub)
        other_priv = generate_private_key()
        with self.assertRaises(Exception):
            decrypt_aes_key(encrypted, other_priv)

    def test_tampered_ciphertext_fails(self):
        encrypted = encrypt_aes_key(self.aes_key, self.pub)
        tampered = bytearray(encrypted)
        tampered[10] ^= 0xFF
        with self.assertRaises(Exception):
            decrypt_aes_key(bytes(tampered), self.priv)


class TestKeyExchangeFlow(unittest.TestCase):
    """模拟完整的密钥协商 + 加密传输流程"""

    def test_full_hybrid_encryption_flow(self):
        from crypto.aes_gcm import generate_aes_key, encrypt_block, decrypt_block

        # 服务端生成 RSA 密钥对
        server_priv = generate_private_key()
        server_pub = generate_public_key(server_priv)

        # 服务端签名 challenge 证明身份
        challenge = os.urandom(32)
        sig = sign(challenge, server_priv)
        self.assertTrue(verify_signature(server_pub, challenge, sig))

        # 客户端生成 AES 会话密钥，用服务端公钥加密
        session_key = generate_aes_key()
        encrypted_key = encrypt_aes_key(session_key, server_pub)

        # 服务端用私钥解密，得到相同的 AES 密钥
        recovered_key = decrypt_aes_key(encrypted_key, server_priv)
        self.assertEqual(recovered_key, session_key)

        # 双方用共享密钥进行 AES-GCM 加密通信
        data = b"Secret file content" * 1000
        block = encrypt_block(data, session_key)
        plaintext = decrypt_block(block.ciphertext, block.nonce, block.tag, recovered_key)
        self.assertEqual(plaintext, data)

    def test_key_exchange_via_pem(self):
        """模拟通过网络传输公钥（PEM 序列化）"""
        server_priv = generate_private_key()
        server_pub = generate_public_key(server_priv)

        # 服务端导出公钥 PEM → 通过 TCP 发送
        pub_pem = export_public_key(server_pub)

        # 客户端从 PEM 加载公钥
        loaded_pub = load_public_key(pub_pem)

        # 客户端用加载的公钥加密 AES 密钥
        aes_key = os.urandom(32)
        encrypted = encrypt_aes_key(aes_key, loaded_pub)

        # 服务端用私钥解密
        decrypted = decrypt_aes_key(encrypted, server_priv)
        self.assertEqual(decrypted, aes_key)


if __name__ == "__main__":
    unittest.main(verbosity=2)
