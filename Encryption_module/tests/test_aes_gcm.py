"""
AES-256-GCM 加解密模块单元测试
运行: python -m pytest tests/test_aes_gcm.py -v
或:  python tests/test_aes_gcm.py
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from cryptography.exceptions import InvalidTag
from crypto.aes_gcm import (
    AesGcmBlock,
    IV_SIZE,
    KEY_SIZE,
    TAG_SIZE,
    decrypt_block,
    encrypt_block,
    generate_aes_key,
)


class TestGenerateAesKey(unittest.TestCase):
    def test_key_length(self):
        key = generate_aes_key()
        self.assertEqual(len(key), KEY_SIZE)
        self.assertEqual(len(key), 32)

    def test_key_randomness(self):
        keys = {generate_aes_key() for _ in range(100)}
        self.assertEqual(len(keys), 100)

    def test_key_is_bytes(self):
        self.assertIsInstance(generate_aes_key(), bytes)


class TestEncryptBlock(unittest.TestCase):
    def setUp(self):
        self.key = generate_aes_key()

    def test_returns_aes_gcm_block(self):
        block = encrypt_block(b"hello", self.key)
        self.assertIsInstance(block, AesGcmBlock)

    def test_nonce_size(self):
        block = encrypt_block(b"hello", self.key)
        self.assertEqual(len(block.nonce), IV_SIZE)
        self.assertEqual(len(block.nonce), 12)

    def test_tag_size(self):
        block = encrypt_block(b"hello", self.key)
        self.assertEqual(len(block.tag), TAG_SIZE)
        self.assertEqual(len(block.tag), 16)

    def test_ciphertext_length_equals_plaintext(self):
        plaintext = b"x" * 1234
        block = encrypt_block(plaintext, self.key)
        self.assertEqual(len(block.ciphertext), len(plaintext))

    def test_nonce_random_each_call(self):
        nonces = {encrypt_block(b"same", self.key).nonce for _ in range(50)}
        self.assertEqual(len(nonces), 50)

    def test_same_input_different_ciphertext(self):
        b1 = encrypt_block(b"same plaintext", self.key)
        b2 = encrypt_block(b"same plaintext", self.key)
        self.assertNotEqual(b1.ciphertext, b2.ciphertext)


class TestDecryptBlock(unittest.TestCase):
    def setUp(self):
        self.key = generate_aes_key()

    def test_roundtrip_short(self):
        plaintext = b"Hello, AES-GCM!"
        block = encrypt_block(plaintext, self.key)
        recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, self.key)
        self.assertEqual(recovered, plaintext)

    def test_roundtrip_empty(self):
        plaintext = b""
        block = encrypt_block(plaintext, self.key)
        recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, self.key)
        self.assertEqual(recovered, plaintext)

    def test_roundtrip_1mb(self):
        plaintext = os.urandom(1024 * 1024)
        block = encrypt_block(plaintext, self.key)
        recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, self.key)
        self.assertEqual(recovered, plaintext)

    def test_roundtrip_binary(self):
        plaintext = bytes(range(256)) * 100
        block = encrypt_block(plaintext, self.key)
        recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, self.key)
        self.assertEqual(recovered, plaintext)


class TestTamperDetection(unittest.TestCase):
    def setUp(self):
        self.key = generate_aes_key()
        self.plaintext = b"Sensitive data that must not be tampered with." * 10
        self.block = encrypt_block(self.plaintext, self.key)

    def test_tampered_ciphertext_raises(self):
        tampered = bytearray(self.block.ciphertext)
        tampered[0] ^= 0xFF
        with self.assertRaises(InvalidTag):
            decrypt_block(bytes(tampered), self.block.nonce, self.block.tag, self.key)

    def test_tampered_tag_raises(self):
        tampered = bytearray(self.block.tag)
        tampered[-1] ^= 0x01
        with self.assertRaises(InvalidTag):
            decrypt_block(self.block.ciphertext, self.block.nonce, bytes(tampered), self.key)

    def test_tampered_nonce_raises(self):
        tampered = bytearray(self.block.nonce)
        tampered[0] ^= 0x01
        with self.assertRaises(InvalidTag):
            decrypt_block(self.block.ciphertext, bytes(tampered), self.block.tag, self.key)

    def test_wrong_key_raises(self):
        wrong_key = generate_aes_key()
        with self.assertRaises(InvalidTag):
            decrypt_block(self.block.ciphertext, self.block.nonce, self.block.tag, wrong_key)

    def test_truncated_ciphertext_raises(self):
        if len(self.block.ciphertext) > 1:
            with self.assertRaises(InvalidTag):
                decrypt_block(
                    self.block.ciphertext[:-1], self.block.nonce, self.block.tag, self.key
                )


class TestKeyIsolation(unittest.TestCase):
    def test_different_keys_dont_decrypt(self):
        key1 = generate_aes_key()
        key2 = generate_aes_key()
        block = encrypt_block(b"secret", key1)
        with self.assertRaises(InvalidTag):
            decrypt_block(block.ciphertext, block.nonce, block.tag, key2)


class TestMultiBlockSimulation(unittest.TestCase):
    """模拟 TCP 模块对一个文件分块循环加解密的场景"""

    def test_multiple_blocks_same_key(self):
        key = generate_aes_key()
        blocks_plain = [os.urandom(1024 * 1024) for _ in range(8)]
        encrypted = [encrypt_block(b, key) for b in blocks_plain]
        nonces = {e.nonce for e in encrypted}
        self.assertEqual(len(nonces), len(encrypted), "每块 nonce 必须不同")
        recovered = [
            decrypt_block(e.ciphertext, e.nonce, e.tag, key) for e in encrypted
        ]
        self.assertEqual(recovered, blocks_plain)


if __name__ == "__main__":
    unittest.main(verbosity=2)
