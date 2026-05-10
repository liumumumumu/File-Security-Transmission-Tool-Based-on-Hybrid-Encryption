from rsa_manager import RSAManager
from key_negotiator import Negotiator, HandshakeError # 记得导入新加的异常类
import logging

class MockSocket:
    def __init__(self):
        self.buffer = b""
    def sendall(self, data):
        self.buffer = data
    def recv(self, bufsize):
        return self.buffer

def run_test():
    print("====== 🚀 开始安全握手本地 Mock 测试 ======\n")

    # 1. 准备阶段：加载 RSA 密钥 (高容错版)
    try:
        priv_key = RSAManager.load_private_key()
        pub_key = RSAManager.load_public_key()
        print("[测试系统] 成功加载本地受保护的 RSA 密钥.")
    except (FileNotFoundError, TypeError) as e:
        # 捕获 TypeError：意味着读到了旧的未加密文件
        # 捕获 FileNotFoundError：意味着没有文件
        print(f"[测试系统] 检测到旧版密钥或未找到密钥 (异常: {type(e).__name__})。")
        print("[测试系统] 正在重新生成高安全等级(带密码保护)的 RSA 密钥...")
        RSAManager.generate_and_save_keys()
        
        # 重新加载刚生成的新密钥
        priv_key = RSAManager.load_private_key()
        pub_key = RSAManager.load_public_key()

    # 2. 准备阶段：拔插一条“模拟网线”
    mock_connection = MockSocket()

    # 3. 客户端行动
    print("\n--- 模拟客户端执行 ---")
    client_aes_key = Negotiator.client_negotiate_key(mock_connection, pub_key)

    # 4. 服务端行动
    print("\n--- 模拟服务端执行 ---")
    server_aes_key = Negotiator.server_negotiate_key(mock_connection, priv_key)

    # 5. 见证奇迹的时刻：比对密钥
    print("\n====== 📊 验证结果 ======")
    print(f"客户端衍生的最终 AES 密钥: {client_aes_key[:8].hex()}...")
    print(f"服务端衍生的最终 AES 密钥: {server_aes_key[:8].hex()}...")

    if client_aes_key == server_aes_key:
        print("\n✅ 测试完美通过！")
        print("🎉 恭喜！基于 HKDF 的预主密钥协商流程执行成功！")
    else:
        print("\n❌ 测试失败：两边的密钥不一致。")

if __name__ == "__main__":
    run_test()