"""隔离验证：模拟 OneBot 协议端连入反向 WS，推一条群消息，验证插件回包。
纯标准库实现，仅连本机回环 8668，不触碰任何真实 bot。"""
import socket, base64, os, json, time, struct

HOST, PORT, PATH = "127.0.0.1", 8668, "/onebot/ws"
TOKEN = os.environ.get("ONEBOT_TOKEN", "")  # 与 application.yml 的 ${ONEBOT_TOKEN:} 对齐，留空则不过 token
SELF = "10001"


def connect():
    s = socket.create_connection((HOST, PORT), timeout=10)
    key = base64.b64encode(os.urandom(16)).decode()
    req = (
        f"GET {PATH} HTTP/1.1\r\nHost: {HOST}:{PORT}\r\nUpgrade: websocket\r\n"
        f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n"
        f"Authorization: Bearer {TOKEN}\r\nX-Self-ID: {SELF}\r\n\r\n"
    )
    s.sendall(req.encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        buf += s.recv(4096)
    assert b"101" in buf.split(b"\r\n")[0], buf[:200]
    return s


def send_text(sock, text):
    """客户端->服务端帧必须带 mask 位，否则 Tomcat 以 1002 断连。"""
    p = text.encode()
    h = bytearray([0x81])
    n = len(p)
    if n < 126:
        h.append(0x80 | n)
    elif n < 65536:
        h.append(0x80 | 126); h += struct.pack(">H", n)
    else:
        h.append(0x80 | 127); h += struct.pack(">Q", n)
    m = os.urandom(4)
    h += m
    sock.sendall(bytes(h) + bytes(b ^ m[i % 4] for i, b in enumerate(p)))


def recv_frames(sock, timeout=3.0):
    sock.settimeout(timeout)
    out, buf = [], b""
    end = time.time() + timeout
    while time.time() < end:
        try:
            d = sock.recv(65536)
            if not d:
                break
            buf += d
        except socket.timeout:
            break
        while len(buf) >= 2:
            b1, b2 = buf[0], buf[1]
            ln = b2 & 0x7F
            off = 2
            if ln == 126:
                if len(buf) < 4: break
                ln = struct.unpack(">H", buf[2:4])[0]; off = 4
            elif ln == 127:
                if len(buf) < 10: break
                ln = struct.unpack(">Q", buf[2:10])[0]; off = 10
            if len(buf) < off + ln: break
            payload = buf[off:off + ln]
            buf = buf[off + ln:]
            if (b1 & 0x0F) == 0x1:
                out.append(payload.decode("utf-8", "replace"))
    return out


s = connect()
print("HANDSHAKE OK")
send_text(s, json.dumps({"post_type": "meta_event", "meta_event_type": "lifecycle",
                         "sub_type": "connect", "self_id": SELF, "time": int(time.time())}))
time.sleep(0.5)

msg = {"post_type": "message", "message_type": "group", "sub_type": "normal", "self_id": SELF,
       "user_id": "20002", "group_id": "30003", "message_id": 42, "time": int(time.time()),
       "sender": {"user_id": "20002", "nickname": "Tester", "role": "member"},
       "message": [{"type": "at", "data": {"qq": SELF}},
                   {"type": "text", "data": {"text": "hello 世界"}}]}
send_text(s, json.dumps(msg))
print("SENT group @bot message: hello 世界")

# 框架的 send 会阻塞等待 OneBot API 响应，必须回 echo 才能完成
deadline = time.time() + 6
got_action = False
while time.time() < deadline:
    for f in recv_frames(s, 1.5):
        print("FRAME>>", f[:300])
        try:
            o = json.loads(f)
        except Exception:
            continue
        if o.get("action"):
            got_action = True
            send_text(s, json.dumps({"status": "ok", "retcode": 0,
                                     "data": {"message_id": 999}, "echo": o.get("echo")}))
            print("ECHOED API response for action:", o.get("action"))
    if got_action:
        break

print("RESULT:", "PASS - 插件回包已收到" if got_action else "FAIL - 未收到 action 帧")
s.close()
