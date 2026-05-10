"""
RSA-2048 密钥协商模块

本模块实现"混合加密"中的非对称加密部分：
  1. RSA-2048 密钥对生成（公钥 + 私钥）
  2. 数字签名 / 验签（身份认证，防止中间人攻击）
  3. 用 RSA 公钥加密 AES 会话密钥 / 用 RSA 私钥解密（密钥协商）

整体流程（与 TCP / AES 模块配合）：
  ┌─────────┐                          ┌─────────┐
  │  客户端  │                          │  服务端  │
  └────┬────┘                          └────┬────┘
       │  1. 请求服务端公钥                  │
       │ ──────────────────────────────────> │
       │                                    │
       │  2. 服务端发送 RSA 公钥             │
       │ <────────────────────────────────── │
       │                                    │
       │  3. 客户端生成随机 AES 会话密钥     │
       │     用服务端公钥加密 AES 密钥       │
       │     发送加密后的 AES 密钥           │
       │ ──────────────────────────────────> │
       │                                    │
       │  4. 服务端用私钥解密，得到 AES 密钥 │
       │     双方现在共享同一把 AES 密钥     │
       │                                    │
       │  5. 后续数据用 AES-256-GCM 加密传输 │
       │ <═════════════════════════════════> │

为什么不直接用 RSA 加密文件？
  - RSA 加密很慢（比 AES 慢 1000 倍以上）
  - RSA 一次只能加密少量数据（2048 位 RSA 最多加密 245 字节）
  - 所以用 RSA 只加密 32 字节的 AES 密钥，再用 AES 加密大文件，这就是"混合加密"
"""
import hashlib
import os
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes, serialization


# ── 常量定义 ──────────────────────────────────────────────

RSA_KEY_SIZE = 2048  # RSA 密钥长度（位），2048 位是目前的安全下限
RSA_PUBLIC_EXPONENT = 65537  # RSA 公钥指数，65537 是行业标准值（兼顾安全与速度）


# ── 密钥对生成 ────────────────────────────────────────────

def generate_private_key() -> rsa.RSAPrivateKey:
    """
    生成 RSA-2048 私钥

    私钥包含完整的密钥信息（p, q, d, n, e），必须严格保密。
    拥有私钥的一方可以：
      - 解密别人用对应公钥加密的数据
      - 对数据进行数字签名（证明"这是我发的"）

    返回:
        RSAPrivateKey 对象（内存中的密钥，可用 export_private_key() 导出为文件）
    """
    return rsa.generate_private_key(
        public_exponent=RSA_PUBLIC_EXPONENT,
        key_size=RSA_KEY_SIZE,
    )


def generate_public_key(private_key: rsa.RSAPrivateKey) -> rsa.RSAPublicKey:
    """
    从私钥中提取对应的公钥

    公钥只包含 (n, e)，可以公开发送给任何人。
    拥有公钥的一方可以：
      - 用它加密数据（只有持有对应私钥的人才能解密）
      - 验证数字签名（确认数据确实是私钥持有者发送的）

    参数:
        private_key: RSA 私钥对象
    返回:
        RSAPublicKey 对象
    """
    return private_key.public_key()


# ── 密钥序列化（导出 / 导入）─────────────────────────────

def export_private_key(private_key: rsa.RSAPrivateKey) -> bytes:
    """
    将私钥导出为 PEM 格式的字节串（可保存为 .pem 文件）

    PEM 格式示例:
      -----BEGIN PRIVATE KEY-----
      MIIEvQIBADANBg...
      -----END PRIVATE KEY-----

    注意: 这里没有加密私钥文件，生产环境中应该加密码保护
    """
    return private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )


def export_public_key(public_key: rsa.RSAPublicKey) -> bytes:
    """
    将公钥导出为 PEM 格式的字节串（可保存为 .pem 文件或通过网络发送）

    PEM 格式示例:
      -----BEGIN PUBLIC KEY-----
      MIIBIjANBg...
      -----END PUBLIC KEY-----
    """
    return public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )


def load_private_key(pem_data: bytes) -> rsa.RSAPrivateKey:
    """
    从 PEM 字节串加载私钥（从文件读取或网络接收后还原）

    参数:
        pem_data: PEM 格式的私钥字节串
    返回:
        RSAPrivateKey 对象
    """
    return serialization.load_pem_private_key(pem_data, password=None)


def load_public_key(pem_data: bytes) -> rsa.RSAPublicKey:
    """
    从 PEM 字节串加载公钥

    参数:
        pem_data: PEM 格式的公钥字节串
    返回:
        RSAPublicKey 对象
    """
    return serialization.load_pem_public_key(pem_data)


# ── 公钥指纹 ──────────────────────────────────────────────

def public_key_fingerprint(public_key: rsa.RSAPublicKey) -> str:
    """
    计算公钥的 SHA-256 指纹，用作账号 ID

    TCP 模块的 Java 端用这个指纹来标识用户身份：
      accountId = CryptoSupport.publicKeyFingerprint(publicKey)

    参数:
        public_key: RSA 公钥对象
    返回:
        64 字符的十六进制字符串（SHA-256 摘要）
    """
    der_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return hashlib.sha256(der_bytes).hexdigest()


# ── 数字签名 / 验签 ──────────────────────────────────────
#
# 数字签名的作用：身份认证
# 场景：服务端要证明"这个公钥确实是我的，不是中间人伪造的"
#   1. 服务端用私钥对一段 challenge（随机数）签名
#   2. 客户端用服务端公钥验证签名
#   3. 如果验证通过，说明对方确实持有私钥，即确实是服务端

def sign(challenge: bytes, private_key: rsa.RSAPrivateKey) -> bytes:
    """
    用 RSA 私钥对 challenge 进行数字签名

    使用 PSS 填充 + SHA-256 哈希（目前最安全的 RSA 签名方案）

    参数:
        challenge:   要签名的数据（通常是对方发来的随机数）
        private_key: 签名方的 RSA 私钥
    返回:
        签名字节串（长度等于 RSA 密钥长度 / 8 = 256 字节）
    """
    return private_key.sign(
        challenge,
        padding.PSS(
            mgf=padding.MGF1(hashes.SHA256()),  # 掩码生成函数
            salt_length=padding.PSS.MAX_LENGTH,  # 盐值长度，越长越安全
        ),
        hashes.SHA256(),
    )


def verify_signature(
    public_key: rsa.RSAPublicKey,
    challenge: bytes,
    signature: bytes,
) -> bool:
    """
    用 RSA 公钥验证数字签名

    参数:
        public_key: 签名方的 RSA 公钥
        challenge:  被签名的原始数据
        signature:  签名字节串
    返回:
        True = 签名有效（对方确实持有对应私钥）
        False = 签名无效（数据被篡改，或签名者不是公钥对应的私钥持有者）
    """
    try:
        public_key.verify(
            signature,
            challenge,
            padding.PSS(
                mgf=padding.MGF1(hashes.SHA256()),
                salt_length=padding.PSS.MAX_LENGTH,
            ),
            hashes.SHA256(),
        )
        return True
    except Exception:
        return False


# ── AES 会话密钥的 RSA 加密 / 解密（密钥协商核心）───────
#
# 这是"混合加密"的关键步骤：
#   客户端生成 AES 密钥 → 用服务端 RSA 公钥加密 → 发送 → 服务端用私钥解密
#   之后双方都有同一把 AES 密钥，就可以用 AES-GCM 加密文件数据了

def encrypt_aes_key(aes_key: bytes, public_key: rsa.RSAPublicKey) -> bytes:
    """
    用 RSA 公钥加密 AES 会话密钥

    使用 OAEP 填充 + SHA-256（目前最安全的 RSA 加密填充方案）
    RSA-2048 + OAEP-SHA256 最多能加密 190 字节，AES 密钥只有 32 字节，绰绰有余

    参数:
        aes_key:    32 字节 AES-256 会话密钥（由 crypto.aes_gcm.generate_aes_key() 生成）
        public_key: 接收方的 RSA 公钥
    返回:
        加密后的 AES 密钥（256 字节，即 RSA 密钥长度 / 8）
    """
    return public_key.encrypt(
        aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),  # 掩码生成函数
            algorithm=hashes.SHA256(),                     # 哈希算法
            label=None,                                    # OAEP label，通常不用
        ),
    )


def decrypt_aes_key(
    encrypted_aes_key: bytes,
    private_key: rsa.RSAPrivateKey,
) -> bytes:
    """
    用 RSA 私钥解密 AES 会话密钥

    参数:
        encrypted_aes_key: 加密后的 AES 密钥（由 encrypt_aes_key() 产出）
        private_key:       接收方的 RSA 私钥
    返回:
        32 字节 AES-256 会话密钥（可直接用于 crypto.aes_gcm.encrypt_block()）
    """
    return private_key.decrypt(
        encrypted_aes_key,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None,
        ),
    )


# ── 自测代码 ──────────────────────────────────────────────

if __name__ == '__main__':
    import sys
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

    print("=" * 50)
    print("RSA-2048 密钥协商模块自测")
    print("=" * 50)

    # --- 测试 1: 密钥对生成与导出/导入 ---
    print("\n[1] 密钥对生成")
    priv = generate_private_key()
    pub = generate_public_key(priv)
    print(f"  私钥类型: {type(priv).__name__}")
    print(f"  公钥类型: {type(pub).__name__}")
    print(f"  密钥长度: {priv.key_size} 位")

    priv_pem = export_private_key(priv)
    pub_pem = export_public_key(pub)
    print(f"  私钥 PEM 长度: {len(priv_pem)} 字节")
    print(f"  公钥 PEM 长度: {len(pub_pem)} 字节")

    priv_loaded = load_private_key(priv_pem)
    pub_loaded = load_public_key(pub_pem)
    print("  密钥导出/导入: 通过")

    # --- 测试 2: 数字签名 ---
    print("\n[2] 数字签名")
    challenge = os.urandom(32)
    sig = sign(challenge, priv)
    print(f"  challenge(hex): {challenge.hex()}")
    print(f"  签名长度: {len(sig)} 字节")

    ok = verify_signature(pub, challenge, sig)
    print(f"  验签(正确公钥): {ok}")
    assert ok, "合法签名验证失败!"

    # 用错误的密钥验签
    other_priv = generate_private_key()
    other_pub = generate_public_key(other_priv)
    bad = verify_signature(other_pub, challenge, sig)
    print(f"  验签(错误公钥): {bad}")
    assert not bad, "非法签名不应验证通过!"
    print("  签名测试: 通过")

    # --- 测试 3: AES 密钥的 RSA 加密/解密 ---
    print("\n[3] AES 密钥加密/解密 (密钥协商)")
    aes_key = os.urandom(32)
    print(f"  原始 AES 密钥(hex): {aes_key.hex()}")

    encrypted = encrypt_aes_key(aes_key, pub)
    print(f"  RSA 加密后长度: {len(encrypted)} 字节")

    decrypted = decrypt_aes_key(encrypted, priv)
    print(f"  RSA 解密后(hex): {decrypted.hex()}")
    assert decrypted == aes_key, "AES 密钥加解密不一致!"
    print("  密钥协商测试: 通过")

    # --- 测试 4: 完整流程模拟 ---
    print("\n[4] 完整混合加密流程模拟")
    print("  服务端生成 RSA 密钥对...")
    server_priv = generate_private_key()
    server_pub = generate_public_key(server_priv)

    print("  客户端生成 AES 会话密钥...")
    from crypto.aes_gcm import generate_aes_key, encrypt_block, decrypt_block
    session_key = generate_aes_key()

    print("  客户端用服务端公钥加密 AES 密钥...")
    encrypted_key = encrypt_aes_key(session_key, server_pub)

    print("  服务端用私钥解密，得到 AES 密钥...")
    recovered_key = decrypt_aes_key(encrypted_key, server_priv)
    assert recovered_key == session_key

    print("  双方用共享 AES 密钥加密传输数据...")
    data = b"This is a secret file content!" * 100
    block = encrypt_block(data, recovered_key)
    plaintext = decrypt_block(block.ciphertext, block.nonce, block.tag, session_key)
    assert plaintext == data
    print("  完整流程测试: 通过!")

    print("\n" + "=" * 50)
    print("全部测试通过!")
    print("=" * 50)
