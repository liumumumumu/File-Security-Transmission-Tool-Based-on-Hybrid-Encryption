"""
AES-256-GCM 性能基准测试
对应题目要求：测量加密开销，对比明文传输 vs 加密传输吞吐率 (MB/s)

运行: python benchmark.py
"""
import os
import time
from crypto.aes_gcm import encrypt_block, decrypt_block, generate_aes_key

BLOCK_SIZE = 1024 * 1024  # 1 MB（与 TCP 模块约定的分块大小一致）
TOTAL_MB = 256            # 测试总数据量 256 MB
NUM_BLOCKS = TOTAL_MB


def human_speed(bytes_count: float, seconds: float) -> str:
    if seconds <= 0:
        return "inf"
    mbps = bytes_count / seconds / (1024 * 1024)
    return f"{mbps:.2f} MB/s"


def bench_baseline_memcopy(blocks: list[bytes]) -> float:
    """基线：纯内存拷贝（模拟无加密的明文传输上限）"""
    start = time.perf_counter()
    sink = bytearray()
    for b in blocks:
        sink += b
    elapsed = time.perf_counter() - start
    return elapsed


def bench_encrypt(blocks: list[bytes], key: bytes):
    start = time.perf_counter()
    encrypted = [encrypt_block(b, key) for b in blocks]
    elapsed = time.perf_counter() - start
    return elapsed, encrypted


def bench_decrypt(encrypted, key: bytes) -> float:
    start = time.perf_counter()
    for e in encrypted:
        decrypt_block(e.ciphertext, e.nonce, e.tag, key)
    elapsed = time.perf_counter() - start
    return elapsed


def main():
    print("=" * 60)
    print("AES-256-GCM 性能基准测试")
    print("=" * 60)
    print(f"块大小:     {BLOCK_SIZE // 1024} KB")
    print(f"块数:       {NUM_BLOCKS}")
    print(f"总数据量:   {TOTAL_MB} MB")
    print()

    print("生成测试数据...")
    blocks = [os.urandom(BLOCK_SIZE) for _ in range(NUM_BLOCKS)]
    key = generate_aes_key()
    total_bytes = BLOCK_SIZE * NUM_BLOCKS

    print("\n[1/3] 基线：内存拷贝（明文上限）")
    t_copy = bench_baseline_memcopy(blocks)
    print(f"      用时 {t_copy:.3f} s, 吞吐 {human_speed(total_bytes, t_copy)}")

    print("\n[2/3] AES-256-GCM 加密")
    t_enc, encrypted = bench_encrypt(blocks, key)
    print(f"      用时 {t_enc:.3f} s, 吞吐 {human_speed(total_bytes, t_enc)}")

    print("\n[3/3] AES-256-GCM 解密")
    t_dec = bench_decrypt(encrypted, key)
    print(f"      用时 {t_dec:.3f} s, 吞吐 {human_speed(total_bytes, t_dec)}")

    print("\n" + "=" * 60)
    print("结果汇总")
    print("=" * 60)
    print(f"{'操作':<20}{'耗时(s)':<12}{'吞吐(MB/s)':<15}{'相对开销':<10}")
    print("-" * 60)
    base_speed = total_bytes / t_copy / (1024 * 1024)
    enc_speed = total_bytes / t_enc / (1024 * 1024)
    dec_speed = total_bytes / t_dec / (1024 * 1024)
    print(f"{'内存拷贝(基线)':<18}{t_copy:<12.3f}{base_speed:<15.2f}{'1.00x':<10}")
    print(f"{'加密':<20}{t_enc:<12.3f}{enc_speed:<15.2f}{base_speed/enc_speed:.2f}x")
    print(f"{'解密':<20}{t_dec:<12.3f}{dec_speed:<15.2f}{base_speed/dec_speed:.2f}x")
    print()
    overhead_pct = (t_enc + t_dec) / t_copy * 100
    print(f"加解密总开销 vs 内存拷贝: {overhead_pct:.1f}%")
    print(
        "注: 实际网络传输瓶颈通常在带宽/磁盘 I/O，"
        f"AES-GCM 单核 ~{enc_speed:.0f} MB/s 通常不会成为瓶颈。"
    )


if __name__ == "__main__":
    main()
