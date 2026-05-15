import requests
import base64
import time
import os

BASE_URL = "http://127.0.0.1:20202"

def print_separator(title):
    print(f"\n{'-'*30}\n▶ {title}\n{'-'*30}")

def test_full_crypto_api():
    print("🚀 开始运行全量自动化测试脚本 (覆盖 17 个接口)...\n")

    # 1. 检查服务器健康状态
    print_separator("1. 测试 GET /health")
    resp = requests.get(f"{BASE_URL}/health")
    print(f"状态码: {resp.status_code}")
    print(f"返回结果: {resp.json()}")

    # 2. 检查密钥状态
    print_separator("2. 测试 GET /key/status")
    resp = requests.get(f"{BASE_URL}/key/status")
    print(f"返回结果: {resp.json()}")

    # 3. 清理环境，删除旧密钥
    print_separator("3. 测试 POST /key/delete (清理环境)")
    resp = requests.post(f"{BASE_URL}/key/delete")
    print(f"返回结果: {resp.json()}")

    # 4. 生成新密钥对
    print_separator("4. 测试 POST /key/generate")
    resp = requests.post(f"{BASE_URL}/key/generate")
    data = resp.json()
    if data.get("success") == "true":
        print("✅ 密钥对生成成功！")
        public_key = data["publicKey"]
        private_key = data["privateKey"]
        print(f"获取到公钥预览: {public_key[:40]}...")
    else:
        print("❌ 密钥生成失败，测试终止。")
        return

    # 5. 单独获取公钥和私钥
    print_separator("5. 测试 GET /key/public 和 GET /key/private")
    pub_resp = requests.get(f"{BASE_URL}/key/public")
    priv_resp = requests.get(f"{BASE_URL}/key/private")
    print(f"成功获取公钥，长度: {len(pub_resp.json()['publicKey'])}")
    print(f"成功获取私钥，长度: {len(priv_resp.json()['privateKey'])}")

    # 5.1 模拟公钥文件丢失：只要私钥存在，服务必须自动推导并恢复公钥
    print_separator("5.1 测试公钥文件丢失后的自动修复")
    health_resp = requests.get(f"{BASE_URL}/health").json()
    public_key_path = os.path.join(health_resp["keyDir"], "public_key.pem")
    if os.path.exists(public_key_path):
        os.remove(public_key_path)
        print(f"已模拟删除公钥文件: {public_key_path}")
    status_after_public_delete = requests.get(f"{BASE_URL}/key/status").json()
    if status_after_public_delete.get("hasPrivateKey") == "true" and status_after_public_delete.get("hasPublicKey") == "true":
        print("✅ 仅公钥文件缺失时，状态仍视为可用。")
    else:
        print(f"❌ 公钥缺失状态不符合预期: {status_after_public_delete}")
    repaired_public_key = requests.get(f"{BASE_URL}/key/public").json()["publicKey"]
    if repaired_public_key == public_key and os.path.exists(public_key_path):
        print("✅ /key/public 已从私钥推导并重新持久化公钥文件。")
    else:
        print("❌ /key/public 未正确修复公钥文件。")

    time.sleep(1)

    # 6. 测试签名与验签 (RSA-PSS)
    print_separator("6. 测试 POST /sign 和 POST /verify")
    sign_data = "This data needs a secure signature."
    sign_payload = {"data": sign_data}
    
    sign_resp = requests.post(f"{BASE_URL}/sign", json=sign_payload).json()
    signature_b64 = sign_resp["signature"]
    print(f"✅ 签名生成成功，签名(Base64)预览: {signature_b64[:40]}...")

    verify_payload = {
        "publicKey": public_key,
        "data": sign_data,
        "signature": signature_b64
    }
    verify_resp = requests.post(f"{BASE_URL}/verify", json=verify_payload).json()
    if verify_resp.get("valid") == "true":
        print("✅ 签名验证成功 (Valid: true)！")
    else:
        print("❌ 签名验证失败！")

    # 7. 测试 RSA 加解密 (OAEP)
    print_separator("7. 测试 POST /rsa/encrypt 和 POST /rsa/decrypt")
    rsa_msg = "A top secret RSA message!"
    rsa_plain_b64 = base64.b64encode(rsa_msg.encode()).decode()
    
    encrypt_resp = requests.post(f"{BASE_URL}/rsa/encrypt", json={"publicKey": public_key, "plain": rsa_plain_b64}).json()
    rsa_cipher = encrypt_resp["cipher"]
    print(f"✅ RSA 加密成功，密文预览: {rsa_cipher[:40]}...")

    decrypt_resp = requests.post(f"{BASE_URL}/rsa/decrypt", json={"cipher": rsa_cipher}).json()
    rsa_decrypted_msg = base64.b64decode(decrypt_resp["plain"]).decode()
    if rsa_decrypted_msg == rsa_msg:
        print("✅ RSA 解密成功，原文一致！")
    else:
        print("❌ RSA 解密失败！")

    time.sleep(1)

    # 8. 测试生成 AES 密钥
    print_separator("8. 测试 POST /aes/generate")
    aes_gen_resp = requests.post(f"{BASE_URL}/aes/generate").json()
    aes_key_b64 = aes_gen_resp["key"]
    print(f"✅ 生成 AES 密钥成功 (Base64): {aes_key_b64}")

    # 9. 测试 AES-GCM 加解密
    print_separator("9. 测试 POST /aes-gcm/encrypt 和 POST /aes-gcm/decrypt")
    aes_msg = "Super fast AES-GCM encrypted stream data."
    aes_plain_b64 = base64.b64encode(aes_msg.encode()).decode()
    
    aes_encrypt_payload = {
        "key": aes_key_b64,
        "plain": aes_plain_b64
    }
    aes_enc_resp = requests.post(f"{BASE_URL}/aes-gcm/encrypt", json=aes_encrypt_payload).json()
    aes_nonce = aes_enc_resp["nonce"]
    aes_ciphertext = aes_enc_resp["ciphertext"]
    aes_tag = aes_enc_resp["tag"]
    print(f"✅ AES-GCM 加密成功！返回了 nonce, ciphertext, tag。")

    aes_decrypt_payload = {
        "key": aes_key_b64,
        "nonce": aes_nonce,
        "ciphertext": aes_ciphertext,
        "tag": aes_tag
    }
    aes_dec_resp = requests.post(f"{BASE_URL}/aes-gcm/decrypt", json=aes_decrypt_payload).json()
    aes_decrypted_msg = base64.b64decode(aes_dec_resp["plain"]).decode()
    
    if aes_decrypted_msg == aes_msg:
        print("✅ AES-GCM 解密成功，原文一致！")
    else:
        print("❌ AES-GCM 解密失败！")

    # 10. 测试获取指纹
    print_separator("10. 测试 POST /key/fingerprint")
    fp_resp = requests.post(f"{BASE_URL}/key/fingerprint", json={"publicKey": public_key}).json()
    print(f"✅ 获取到公钥指纹 (SHA-256): {fp_resp['fingerprint']}")

    # 11. 测试从私钥推导公钥
    print_separator("11. 测试 POST /key/derive-public")
    derive_resp = requests.post(f"{BASE_URL}/key/derive-public", json={"privateKey": private_key}).json()
    derived_public_key = derive_resp["publicKey"]
    if derived_public_key == public_key:
        print("✅ 从私钥推导公钥成功，与原公钥一致！")
    else:
        print("❌ 推导公钥失败！")

    # 12. 测试导入私钥文本 (对应第16个接口)
    print_separator("12. 测试 POST /key/import-text")
    import_text_payload = {"privateKey": private_key}
    import_text_resp = requests.post(f"{BASE_URL}/key/import-text", json=import_text_payload)
    
    if import_text_resp.status_code == 200:
        data = import_text_resp.json()
        if data.get("success") == "true":
            print("✅ 文本导入私钥成功！")
            print(f"成功推导出公钥，预览: {data['publicKey'][:40]}...")
        else:
            print(f"❌ 文本导入失败: {data}")
    else:
        print(f"❌ 文本导入接口报错，状态码: {import_text_resp.status_code}")

    # 13. 测试从文件导入私钥 (对应第17个接口)
    print_separator("13. 测试 POST /key/import-file")
    temp_file_path = os.path.abspath("temp_test_private_key.pem")
    with open(temp_file_path, "w", encoding="utf-8") as f:
        f.write(private_key)
    print(f"临时创建了私钥文件: {temp_file_path}")

    import_file_payload = {"path": temp_file_path}
    import_file_resp = requests.post(f"{BASE_URL}/key/import-file", json=import_file_payload)
    
    if import_file_resp.status_code == 200:
        data = import_file_resp.json()
        if data.get("success") == "true":
            print("✅ 文件路径导入私钥成功！")
            print(f"成功推导出公钥，预览: {data['publicKey'][:40]}...")
        else:
            print(f"❌ 文件导入失败: {data}")
    else:
        print(f"❌ 文件导入接口报错，状态码: {import_file_resp.status_code}")
        
    if os.path.exists(temp_file_path):
        os.remove(temp_file_path)
        print("🧹 已清理临时私钥文件。")

    print("\n🎉🎉🎉 所有 17 个接口测试执行完毕！ 🎉🎉🎉")

if __name__ == "__main__":
    test_full_crypto_api()
