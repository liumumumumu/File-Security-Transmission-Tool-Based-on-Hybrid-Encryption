"""
AES-256-GCM 加解密模块 (对接 TCP 模块约定接口)
"""
import os
from dataclasses import dataclass
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

IV_SIZE = 12   # GCM nonce 长度
TAG_SIZE = 16  # GCM tag 长度
KEY_SIZE = 32  # AES-256 密钥长度


@dataclass
class AesGcmBlock:
    """AES-GCM 加密后的数据块"""
    nonce: bytes       # 加密用的随机数 (12 字节)
    ciphertext: bytes  # 密文
    tag: bytes         # 完整性验证标签 (16 字节)


def generate_aes_key() -> bytes:
    """生成 AES-256 密钥 (32 字节随机数)"""
    return os.urandom(KEY_SIZE)


def encrypt_block(data: bytes, key: bytes) -> AesGcmBlock:
    """
    用 AES-256-GCM 加密一个数据块
    参数:
        data: 明文数据
        key:  AES-256 密钥 (32 字节)
    返回:
        AesGcmBlock 对象 (含 nonce / ciphertext / tag)
    """
    nonce = os.urandom(IV_SIZE)
    aesgcm = AESGCM(key)
    ct_with_tag = aesgcm.encrypt(nonce, data, None)
    ciphertext = ct_with_tag[:-TAG_SIZE]
    tag = ct_with_tag[-TAG_SIZE:]
    return AesGcmBlock(nonce=nonce, ciphertext=ciphertext, tag=tag)


def decrypt_block(ciphertext: bytes, nonce: bytes, tag: bytes, key: bytes) -> bytes:
    """
    用 AES-256-GCM 解密一个数据块
    参数:
        ciphertext: 密文
        nonce:      加密时使用的随机数 (12 字节)
        tag:        完整性标签 (16 字节)
        key:        AES-256 密钥 (32 字节)
    返回:
        明文 bytes
    抛出:
        cryptography.exceptions.InvalidTag - 数据被篡改时抛出
    """
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(nonce, ciphertext + tag, None)


if __name__ == '__main__':
    key = generate_aes_key()
    print(f"AES-256 密钥(hex): {key.hex()}")

    plaintext = b"Hello, AES-256-GCM! This is a test message." * 100
    print(f"明文长度: {len(plaintext)} 字节")

    block = encrypt_block(plaintext, key)
    print(f"nonce(hex): {block.nonce.hex()}")
    print(f"密文长度:   {len(block.ciphertext)} 字节")
    print(f"tag(hex):   {block.tag.hex()}")

    recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, key)
    assert recovered == plaintext, "解密结果与原文不一致!"
    print("测试通过: 加解密一致")

    print("\n篡改测试:")
    tampered = bytearray(block.ciphertext)
    tampered[0] ^= 0xFF
    try:
        decrypt_block(bytes(tampered), block.nonce, block.tag, key)
        print("失败: 应该抛出异常但没有")
    except Exception as e:
        print(f"通过: 检测到篡改 ({type(e).__name__})")
