#!/usr/bin/env python3
"""
Controlled local HTTP test server that supports RFC 7233 / RFC 9110 byte ranges.
Used for testing multi-connection accelerated downloads, single-stream fallback,
and throughput comparison between single-stream and 4-8 parallel connections.
"""

import hashlib
import os
import re
import sys
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from socketserver import ThreadingMixIn

TEST_DATA_SIZE = 16 * 1024 * 1024  # 16 MB
# Generate deterministic pattern
SEED_PATTERN = b"AzureloadByteRangeEngineValidationPayload_2026!#"
REPEATS = (TEST_DATA_SIZE // len(SEED_PATTERN)) + 1
PAYLOAD = (SEED_PATTERN * REPEATS)[:TEST_DATA_SIZE]
PAYLOAD_SHA256 = hashlib.sha256(PAYLOAD).hexdigest()
PAYLOAD_ETAG = f'"{PAYLOAD_SHA256[:16]}"'

# Global statistics
STATS = {
    "requests_total": 0,
    "range_requests": 0,
    "full_requests": 0,
    "head_requests": 0,
    "connections_active": 0,
    "bytes_served": 0,
}

class ThreadedHTTPServer(ThreadingMixIn, HTTPServer):
    daemon_threads = True

class RangeRequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        sys.stderr.write(f"[{time.strftime('%H:%M:%S')}] {self.client_address[0]}:{self.client_address[1]} - {format % args}\n")
        sys.stderr.flush()

    def do_HEAD(self):
        STATS["requests_total"] += 1
        STATS["head_requests"] += 1

        path = self.path.split("?")[0]
        if path == "/no-range.bin":
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(PAYLOAD)))
            self.send_header("ETag", PAYLOAD_ETAG)
            self.end_headers()
            return

        # Default /range-test.bin
        self.send_response(200)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(len(PAYLOAD)))
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("ETag", PAYLOAD_ETAG)
        self.send_header("Content-Disposition", 'attachment; filename="range-test.bin"')
        self.end_headers()

    def do_GET(self):
        STATS["requests_total"] += 1
        path = self.path.split("?")[0]

        if path == "/stats":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            import json
            data = json.dumps({
                **STATS,
                "payload_size": len(PAYLOAD),
                "payload_sha256": PAYLOAD_SHA256
            }, indent=2).encode("utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return

        if path == "/sha256":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            res = PAYLOAD_SHA256.encode("utf-8")
            self.send_header("Content-Length", str(len(res)))
            self.end_headers()
            self.wfile.write(res)
            return

        # Check for simulated per-connection rate limiting e.g. ?rate_limit_kbps=1024
        rate_limit_kbps = 0
        if "?" in self.path:
            query = self.path.split("?")[1]
            params = dict(p.split("=") for p in query.split("&") if "=" in p)
            rate_limit_kbps = int(params.get("rate_limit_kbps", 0))

        range_header = self.headers.get("Range")

        # Endpoint that refuses byte ranges (like HEXAQA)
        if path == "/no-range.bin":
            STATS["full_requests"] += 1
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(PAYLOAD)))
            self.send_header("Content-Disposition", 'attachment; filename="no-range.bin"')
            self.end_headers()
            self._stream_bytes(PAYLOAD, rate_limit_kbps)
            return

        # Range-supporting endpoint
        if range_header:
            STATS["range_requests"] += 1
            match = re.match(r"^bytes=(\d+)-(\d+)?$", range_header.strip())
            if not match:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{len(PAYLOAD)}")
                self.end_headers()
                return

            start = int(match.group(1))
            end_raw = match.group(2)
            end = int(end_raw) if end_raw else len(PAYLOAD) - 1

            if start >= len(PAYLOAD) or start > end:
                self.send_response(416)
                self.send_header("Content-Range", f"bytes */{len(PAYLOAD)}")
                self.end_headers()
                return

            end = min(end, len(PAYLOAD) - 1)
            content_length = end - start + 1
            slice_data = PAYLOAD[start:end + 1]

            self.send_response(206)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Range", f"bytes {start}-{end}/{len(PAYLOAD)}")
            self.send_header("Content-Length", str(content_length))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("ETag", PAYLOAD_ETAG)
            self.send_header("Content-Disposition", 'attachment; filename="range-test.bin"')
            self.end_headers()
            self._stream_bytes(slice_data, rate_limit_kbps)
        else:
            STATS["full_requests"] += 1
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", str(len(PAYLOAD)))
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("ETag", PAYLOAD_ETAG)
            self.send_header("Content-Disposition", 'attachment; filename="range-test.bin"')
            self.end_headers()
            self._stream_bytes(PAYLOAD, rate_limit_kbps)

    def _stream_bytes(self, data, rate_limit_kbps=0):
        STATS["connections_active"] += 1
        chunk_size = 64 * 1024
        bytes_per_sec = (rate_limit_kbps * 1024) if rate_limit_kbps > 0 else 0

        try:
            offset = 0
            while offset < len(data):
                t0 = time.time()
                chunk = data[offset:offset + chunk_size]
                self.wfile.write(chunk)
                offset += len(chunk)
                STATS["bytes_served"] += len(chunk)

                if bytes_per_sec > 0:
                    expected_duration = len(chunk) / bytes_per_sec
                    elapsed = time.time() - t0
                    if elapsed < expected_duration:
                        time.sleep(expected_duration - elapsed)
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            STATS["connections_active"] -= 1

def run_server(port=18080):
    server_address = ("127.0.0.1", port)
    httpd = ThreadedHTTPServer(server_address, RangeRequestHandler)
    print(f"Starting Range Test Server on http://127.0.0.1:{port}")
    print(f"Payload Size: {len(PAYLOAD)} bytes ({len(PAYLOAD)/(1024*1024):.1f} MB)")
    print(f"SHA-256: {PAYLOAD_SHA256}")
    sys.stdout.flush()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        httpd.server_close()
        print("Server stopped.")

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    run_server(port)
