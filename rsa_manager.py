import logging
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

# 1. 配置工业级日志输出
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - [%(levelname)s] - %(message)s'
)
logger = logging.getLogger("RSAManager")

class RSAManager:
    # 模拟真实环境：私钥必须通过密码保护才能保存在硬盘上
    PEM_PASSPHRASE = b"Enterprise_Sec_2026!@" 

    @staticmethod
    def generate_and_save_keys(private_path="server_private.pem", public_path="server_public.pem"):
        logger.info("开始生成 RSA-2048 密钥对...")
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048,
            backend=default_backend()
        )
        public_key = private_key.public_key()

        # 高档操作：使用最佳可用加密算法 (BestAvailableEncryption) 保护私钥文件
        with open(private_path, "wb") as f:
            f.write(private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.BestAvailableEncryption(RSAManager.PEM_PASSPHRASE)
            ))

        with open(public_path, "wb") as f:
            f.write(public_key.public_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PublicFormat.SubjectPublicKeyInfo
            ))
        logger.info(f"密钥对已安全落盘: {private_path} (已加密), {public_path}")

    @staticmethod
    def load_private_key(file_path="server_private.pem"):
        logger.info(f"正在加载受保护的私钥: {file_path}")
        with open(file_path, "rb") as key_file:
            return serialization.load_pem_private_key(
                key_file.read(),
                password=RSAManager.PEM_PASSPHRASE, # 必须提供密码才能加载
                backend=default_backend()
            )

    @staticmethod
    def load_public_key(file_path="server_public.pem"):
        logger.info(f"正在加载公钥: {file_path}")
        with open(file_path, "rb") as key_file:
            return serialization.load_pem_public_key(
                key_file.read(),
                backend=default_backend()
            )