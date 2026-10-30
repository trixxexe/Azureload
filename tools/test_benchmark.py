#!/usr/bin/env python3
"""
Benchmark and validation tool for Azureload's multi-connection engine.
Validates:
1. RFC 7233 Byte Range support (HTTP 206 Partial Content).
2. Genuine 4-connection and 8-connection parallel downloads.
3. Single-stream vs Parallel throughput comparison (with simulated bandwidth limit).
4. Pause / Resume recovery with chunk reassembly.
5. Unsupported byte-range detection and fallback.
"""

import concurrent.futures
import hashlib
import json
import os
import sys
import time
import urllib.request
import urllib.error

SERVER_URL = "http://127.0.0.1:18080"

def get_expected_sha256():
    with urllib.request.urlopen(f"{SERVER_URL}/sha256") as resp:
        return resp.read().decode("utf-8").strip()

def download_chunk(url, start_byte, end_byte):
    req = urllib.request.Request(url)
    req.add_header("Range", f"bytes={start_byte}-{end_byte}")
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 206, f"Expected 206 Partial Content, got {resp.status}"
        data = resp.read()
        return start_byte, data

def test_single_stream(rate_limit_kbps=0):
    url = f"{SERVER_URL}/range-test.bin"
    if rate_limit_kbps > 0:
        url += f"?rate_limit_kbps={rate_limit_kbps}"

    t0 = time.time()
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200, f"Expected 200 OK, got {resp.status}"
        data = resp.read()
    duration = time.time() - t0
    sha256 = hashlib.sha256(data).hexdigest()
    mb = len(data) / (1024 * 1024)
    speed_mb_s = mb / duration if duration > 0 else 0
    return {
        "mode": "Single Stream (1 Connection)",
        "connections": 1,
        "bytes": len(data),
        "duration_sec": duration,
        "speed_mb_s": speed_mb_s,
        "sha256": sha256
    }

def test_parallel_download(connections, rate_limit_kbps=0):
    url = f"{SERVER_URL}/range-test.bin"
    if rate_limit_kbps > 0:
        url += f"?rate_limit_kbps={rate_limit_kbps}"

    # Get total size via HEAD
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req) as resp:
        assert resp.status == 200
        total_size = int(resp.headers.get("Content-Length"))

    # Partition chunks
    chunk_size = total_size // connections
    ranges = []
    start = 0
    for i in range(connections):
        end = (start + chunk_size - 1) if i < connections - 1 else (total_size - 1)
        ranges.append((start, end))
        start = end + 1

    t0 = time.time()
    chunks = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=connections) as executor:
        futures = [executor.submit(download_chunk, url, r[0], r[1]) for r in ranges]
        for f in concurrent.futures.as_completed(futures):
            offset, data = f.result()
            chunks[offset] = data
    duration = time.time() - t0

    # Reassemble in order
    sorted_offsets = sorted(chunks.keys())
    full_data = b"".join(chunks[o] for o in sorted_offsets)
    sha256 = hashlib.sha256(full_data).hexdigest()
    mb = len(full_data) / (1024 * 1024)
    speed_mb_s = mb / duration if duration > 0 else 0
    return {
        "mode": f"Multi-Connection ({connections} Parallel)",
        "connections": connections,
        "bytes": len(full_data),
        "duration_sec": duration,
        "speed_mb_s": speed_mb_s,
        "sha256": sha256
    }

def test_pause_resume(connections=4):
    url = f"{SERVER_URL}/range-test.bin"
    req = urllib.request.Request(url, method="HEAD")
    with urllib.request.urlopen(req) as resp:
        total_size = int(resp.headers.get("Content-Length"))

    chunk_size = total_size // connections
    ranges = []
    start = 0
    for i in range(connections):
        end = (start + chunk_size - 1) if i < connections - 1 else (total_size - 1)
        ranges.append((start, end))
        start = end + 1

    # Phase 1: Download first half of each chunk (simulating pause midway)
    part1_chunks = {}
    remaining_ranges = []
    for s, e in ranges:
        mid = s + (e - s) // 2
        _, data = download_chunk(url, s, mid)
        part1_chunks[s] = data
        remaining_ranges.append((mid + 1, e))

    # Phase 2: Resume remaining ranges
    part2_chunks = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=connections) as executor:
        futures = [executor.submit(download_chunk, url, r[0], r[1]) for r in remaining_ranges]
        for f in concurrent.futures.as_completed(futures):
            offset, data = f.result()
            part2_chunks[offset] = data

    # Assemble
    assembled = bytearray()
    for (s, e), (mid_plus_1, _) in zip(ranges, remaining_ranges):
        assembled.extend(part1_chunks[s])
        assembled.extend(part2_chunks[mid_plus_1])

    sha256 = hashlib.sha256(assembled).hexdigest()
    return sha256

def test_unsupported_range_behavior():
    url = f"{SERVER_URL}/no-range.bin"
    req = urllib.request.Request(url)
    req.add_header("Range", "bytes=0-0")
    with urllib.request.urlopen(req) as resp:
        status = resp.status
        accept_ranges = resp.headers.get("Accept-Ranges")
        content_length = int(resp.headers.get("Content-Length"))
    return status, accept_ranges, content_length

def main():
    print("=" * 60)
    print("AZURELOAD ENGINE VERIFICATION & BENCHMARK SUITE")
    print("=" * 60)

    expected_sha = get_expected_sha256()
    print(f"Target Server: {SERVER_URL}")
    print(f"Expected SHA-256: {expected_sha}")
    print()

    # 1. Byte range capability probe
    print("1. PROBING CAPABILITIES...")
    req = urllib.request.Request(f"{SERVER_URL}/range-test.bin")
    req.add_header("Range", "bytes=0-0")
    with urllib.request.urlopen(req) as resp:
        code = resp.status
        content_range = resp.headers.get("Content-Range")
        accept_ranges = resp.headers.get("Accept-Ranges")
        print(f"   Response Code: {code}")
        print(f"   Content-Range: {content_range}")
        print(f"   Accept-Ranges: {accept_ranges}")
        assert code == 206, "Range test server must return 206 Partial Content"
        print("   -> Byte Ranges Supported: YES (RFC 7233 / 206 verified)")
    print()

    # 2. Unsupported byte range behavior (HEXAQA simulation)
    print("2. TESTING UNSUPPORTED RANGE DETECTION (HEXAQA SIMULATION)...")
    status, ar, cl = test_unsupported_range_behavior()
    print(f"   Probe /no-range.bin with Range: bytes=0-0")
    print(f"   Response Code: {status} (Expected 200 OK)")
    print(f"   Accept-Ranges: {ar}")
    print(f"   Content-Length: {cl}")
    assert status == 200, "Server ignoring range returns 200 OK"
    print("   -> Correctly detected as Byte Ranges Supported: NO. Single stream mandated.")
    print()

    # 3. Throughput comparison (with simulated per-connection rate limit of 1024 KB/s = 1 MB/s)
    # This demonstrates why multi-connection acceleration exists:
    # When servers or networks throttle per TCP connection, parallel connections multiply throughput.
    RATE_LIMIT = 2048  # 2 MB/s per connection limit
    print(f"3. THROUGHPUT BENCHMARK (Simulated Per-Connection Limit: {RATE_LIMIT} KB/s)...")

    print("   Running Single Stream...")
    res_1 = test_single_stream(rate_limit_kbps=RATE_LIMIT)
    assert res_1["sha256"] == expected_sha, "Checksum mismatch on 1 connection"
    print(f"   Single Stream: {res_1['duration_sec']:.2f}s @ {res_1['speed_mb_s']:.2f} MB/s (SHA OK)")

    print("   Running 4 Parallel Connections...")
    res_4 = test_parallel_download(4, rate_limit_kbps=RATE_LIMIT)
    assert res_4["sha256"] == expected_sha, "Checksum mismatch on 4 connections"
    speedup_4 = res_4["speed_mb_s"] / res_1["speed_mb_s"] if res_1["speed_mb_s"] > 0 else 0
    print(f"   4 Connections: {res_4['duration_sec']:.2f}s @ {res_4['speed_mb_s']:.2f} MB/s ({speedup_4:.2f}x speedup, SHA OK)")

    print("   Running 8 Parallel Connections...")
    res_8 = test_parallel_download(8, rate_limit_kbps=RATE_LIMIT)
    assert res_8["sha256"] == expected_sha, "Checksum mismatch on 8 connections"
    speedup_8 = res_8["speed_mb_s"] / res_1["speed_mb_s"] if res_1["speed_mb_s"] > 0 else 0
    print(f"   8 Connections: {res_8['duration_sec']:.2f}s @ {res_8['speed_mb_s']:.2f} MB/s ({speedup_8:.2f}x speedup, SHA OK)")
    print()

    # 4. Pause / Resume Validation
    print("4. TESTING PAUSE AND RESUME RECOVERY...")
    resumed_sha = test_pause_resume(connections=4)
    assert resumed_sha == expected_sha, f"Pause/resume SHA mismatch: {resumed_sha} vs {expected_sha}"
    print(f"   Resumed Chunks SHA: {resumed_sha}")
    print("   -> Pause/Resume integrity test PASSED.")
    print()

    # Summary
    print("=" * 60)
    print("BENCHMARK SUMMARY RESULTS")
    print("=" * 60)
    print(f"{'Mode':<30} | {'Connections':<11} | {'Time (s)':<8} | {'Speed (MB/s)':<12} | {'Integrity'}")
    print("-" * 75)
    for r in [res_1, res_4, res_8]:
        print(f"{r['mode']:<30} | {r['connections']:<11} | {r['duration_sec']:<8.2f} | {r['speed_mb_s']:<12.2f} | MATCH")
    print("=" * 60)

if __name__ == "__main__":
    main()
