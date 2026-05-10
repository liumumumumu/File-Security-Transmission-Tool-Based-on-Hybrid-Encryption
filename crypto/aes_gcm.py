"""
AES-256-GCM 加解密模块 (对接 TCP 模块约定接口)

AES-256-GCM 是一种 "认证加密" 算法，同时提供：
  - 机密性：数据被加密，第三方无法读取明文
  - 完整性：通过 GCM Tag 校验，任何篡改都会被检测到

工作流程（以文件传输为例）：
  发送方：明文数据块 --encrypt_block()--> nonce + 密文 + tag --TCP发送-->
  接收方：--TCP接收--> nonce + 密文 + tag --decrypt_block()--> 明文数据块
"""
import os
from dataclasses import dataclass
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# ── 常量定义 ──────────────────────────────────────────────
# 这三个值是 AES-256-GCM 标准规定的，不能随意修改

IV_SIZE = 12   # nonce（随机数）长度，GCM 标准要求 12 字节
TAG_SIZE = 16  # GCM 认证标签长度，16 字节 = 128 位，用于校验数据是否被篡改
KEY_SIZE = 32  # AES-256 的密钥长度，32 字节 = 256 位


# ── 数据结构 ──────────────────────────────────────────────

@dataclass
class AesGcmBlock:
    """
    AES-GCM 加密后的数据块，包含三个部分：

    nonce:      12 字节随机数，加密时随机生成，解密时必须用同一个
                （类似"一次性密码本的编号"，保证同一密钥加密不同数据时密文不同）
    ciphertext: 密文，长度与原始明文相同
    tag:        16 字节认证标签，由 GCM 算法根据密文+密钥+nonce 计算出
                接收方用它来验证数据在传输中是否被篡改
    """
    nonce: bytes
    ciphertext: bytes
    tag: bytes


# ── 核心函数 ──────────────────────────────────────────────

def generate_aes_key() -> bytes:
    """
    生成 AES-256 密钥(32 字节纯随机数）

    os.urandom() 使用操作系统的密码学安全随机源(Linux: /dev/urandom, Windows: CryptGenRandom)
    每次调用生成不同的密钥，用于一次文件传输会话
    """
    return os.urandom(KEY_SIZE)


def encrypt_block(data: bytes, key: bytes) -> AesGcmBlock:
    """
    用 AES-256-GCM 加密一个数据块

    参数:
        data: 要加密的明文（比如从文件读出的 1MB 数据块）
        key:  32 字节 AES-256 密钥（由 generate_aes_key() 生成，或密钥协商得到）
    返回:
        AesGcmBlock 对象，包含 nonce / ciphertext / tag 三个字段
    """
    # 1. 生成 12 字节随机 nonce，每次加密必须不同
    #    如果两次加密用了相同的 nonce + 相同的 key，安全性会被完全破坏
    nonce = os.urandom(IV_SIZE)

    # 2. 创建 AES-GCM 加密器
    aesgcm = AESGCM(key)

    # 3. 执行加密
    #    encrypt() 的第三个参数 None 是 AAD（附加认证数据），这里不需要
    #    返回值 = 密文 + Tag 拼在一起（cryptography 库的约定）
    ct_with_tag = aesgcm.encrypt(nonce, data, None)

    # 4. 把密文和 Tag 拆开（Tag 固定在末尾 16 字节）
    #    拆开是为了符合 TCP 模块约定的接口，方便分别序列化传输
    ciphertext = ct_with_tag[:-TAG_SIZE]
    tag = ct_with_tag[-TAG_SIZE:]

    return AesGcmBlock(nonce=nonce, ciphertext=ciphertext, tag=tag)


def decrypt_block(ciphertext: bytes, nonce: bytes, tag: bytes, key: bytes) -> bytes:
    """
    用 AES-256-GCM 解密一个数据块

    参数:
        ciphertext: 密文（由 encrypt_block 产出）
        nonce:      加密时生成的 12 字节随机数（必须和加密时的一致）
        tag:        16 字节认证标签（必须和加密时的一致）
        key:        32 字节 AES-256 密钥（必须和加密时的一致）
    返回:
        解密后的明文 bytes
    抛出:
        cryptography.exceptions.InvalidTag — 密文/nonce/tag 任何一个被篡改都会触发
    """
    aesgcm = AESGCM(key)

    # decrypt() 要求传入 ciphertext + tag 的拼接体（和 encrypt 的返回格式对应）
    # 内部会先用 tag 验证完整性，通过后才解密返回明文
    # 如果验证失败（数据被篡改），直接抛出 InvalidTag 异常，不会返回任何数据
    return aesgcm.decrypt(nonce, ciphertext + tag, None)


# ── 自测代码 ──────────────────────────────────────────────
# 直接运行 python crypto/aes_gcm.py 即可执行下面的测试

if __name__ == '__main__':
    # --- 测试 1: 加解密一致性 ---
    key = generate_aes_key()
    print(f"AES-256 密钥(hex): {key.hex()}")

    # 构造一段测试明文（重复 100 次，共 4400 字节）
    plaintext = b"Hello, AES-256-GCM! This is a test message." * 100
    print(f"明文长度: {len(plaintext)} 字节")

    # 加密
    block = encrypt_block(plaintext, key)
    print(f"nonce(hex): {block.nonce.hex()}")
    print(f"密文长度:   {len(block.ciphertext)} 字节")
    print(f"tag(hex):   {block.tag.hex()}")

    # 解密并验证与原文一致
    recovered = decrypt_block(block.ciphertext, block.nonce, block.tag, key)
    assert recovered == plaintext, "解密结果与原文不一致!"
    print("测试通过: 加解密一致")

    # --- 测试 2: 篡改检测 ---
    # 模拟中间人攻击：修改密文的第一个字节
    print("\n篡改测试:")
    tampered = bytearray(block.ciphertext)   # 复制一份密文
    tampered[0] ^= 0xFF                      # 翻转第一个字节的所有位
    try:
        decrypt_block(bytes(tampered), block.nonce, block.tag, key)
        print("失败: 应该抛出异常但没有")
    except Exception as e:
        # 预期走到这里：GCM 检测到密文被改过，拒绝解密
        print(f"通过: 检测到篡改 ({type(e).__name__})")
