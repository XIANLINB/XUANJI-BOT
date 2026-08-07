"""保持一条反向 WS 连接 20 秒，用于验证控制台会话列表显示。仅连本机回环。"""
import socket, base64, os, json, time, struct

HOST, PORT, PATH = "127.0.0.1", 8668, "/onebot/ws"
TOKEN = os.environ.get("ONEBOT_TOKEN", "")  # 与 application.yml 的 ${ONEBOT_TOKEN:} 对齐，留空则不过 token
SELF = "10001"

s = socket.create_connection((HOST, PORT), timeout=10)
key = base64.b64encode(os.urandom(16)).decode()
s.sendall((
    f"GET {PATH} HTTP/1.1\r\nHost: {HOST}:{PORT}\r\nUpgrade: websocket\r\n"
    f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
    f"Authorization: Bearer {TOKEN}\r\nX-Self-ID: {SELF}\r\n\r\n"
).encode())
buf = b""
while b"\r\n\r\n" not in buf:
    buf += s.recv(4096)
print("HOLD: handshake", buf.split(b"\r\n")[0].decode(), flush=True)


def send_text(sock, text):
    p = text.encode()
    h = bytearray([0x81])
    n = len(p)
    if n < 126:
        h.append(0x80 | n)
    else:
        h.append(0x80 | 126); h += struct.pack(">H", n)
    m = os.urandom(4)
    h += m
    sock.sendall(bytes(h) + bytes(b ^ m[i % 4] for i, b in enumerate(p)))


send_text(s, json.dumps({"post_type": "meta_event", "meta_event_type": "lifecycle",
                         "sub_type": "connect", "self_id": SELF, "time": int(time.time())}))
print("HOLD: lifecycle sent, holding 20s", flush=True)
time.sleep(20)
s.close()
print("HOLD: closed", flush=True)
