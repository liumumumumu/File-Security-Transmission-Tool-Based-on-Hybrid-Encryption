import os
import logging
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

logger = logging.getLogger("Negotiator")

# 自定义异常类：让程序的错误处理显得非常专业
class HandshakeError(Exception):
    """当握手失败、密钥不匹配或网络异常时抛出"""
    pass

class Negotiator:
    # 填充策略：OAEP + SHA256
    PADDING_STRATEGY = padding.OAEP(
        mgf=padding.MGF1(algorithm=hashes.SHA256()),
        algorithm=hashes.SHA256(),
        label=None
    )

    @staticmethod
    def _derive_master_key(pre_master_secret: bytes) -> bytes:
        """
        高档操作：HKDF 密钥衍生 (参考 TLS 协议规范)
        将 32 字节的预主密钥，通过 HMAC 提取并扩展成真正高强度的 AES 密钥
        """
        hkdf = HKDF(
            algorithm=hashes.SHA256(),
            length=32,
            salt=None,
            info=b"file_transfer_handshake_v1", # 上下文绑定，防止重放攻击
        )
        return hkdf.derive(pre_master_secret)

    @staticmethod
    def client_negotiate_key(connection, server_public_key) -> bytes:
        logger.info("[Client] 初始化安全握手流程...")
        
        # 1. 生成的不再是直接的 AES 密钥，而是“预主密钥”
        pre_master_secret = os.urandom(32)
        
        try:
            # 2. RSA 加密预主密钥
            encrypted_secret = server_public_key.encrypt(
                pre_master_secret,
                Negotiator.PADDING_STRATEGY
            )
            
            # 3. 发送给服务器
            connection.sendall(encrypted_secret)
            logger.info("[Client] 加密的 Pre-Master Secret 已发送.")

            # 4. 本地通过 HKDF 衍生出真正的 AES 密钥
            master_aes_key = Negotiator._derive_master_key(pre_master_secret)
            logger.info("[Client] HKDF 密钥衍生完毕，握手成功！")
            
            return master_aes_key
            
        except Exception as e:
            logger.error(f"[Client] 握手过程发生致命错误: {e}")
            raise HandshakeError("客户端握手失败") from e

    @staticmethod
    def server_negotiate_key(connection, server_private_key) -> bytes:
        logger.info("[Server] 等待客户端发起安全握手...")
        try:
            # 1. 接收 256 字节的密文
            encrypted_secret = connection.recv(256)
            if not encrypted_secret or len(encrypted_secret) != 256:
                raise HandshakeError(f"非法的握手数据包长度: {len(encrypted_secret)}")

            # 2. RSA 私钥解密，获得“预主密钥”
            pre_master_secret = server_private_key.decrypt(
                encrypted_secret,
                Negotiator.PADDING_STRATEGY
            )
            logger.info("[Server] 成功解密获取 Pre-Master Secret.")

            # 3. 本地通过 HKDF 衍生出真正的 AES 密钥 (和客户端过程完全一致)
            master_aes_key = Negotiator._derive_master_key(pre_master_secret)
            logger.info("[Server] HKDF 密钥衍生完毕，握手成功！")

            return master_aes_key

        except ValueError as e:
            logger.error("[Server] RSA 解密失败，可能受到中间人攻击！")
            raise HandshakeError("服务端握手解密失败") from e
        except Exception as e:
            logger.error(f"[Server] 握手异常中断: {e}")
            raise HandshakeError("服务端握手未知错误") from e