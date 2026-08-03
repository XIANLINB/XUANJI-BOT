---
name: xuanji-console-onebot
description: This skill should be used when extending the Xuanji bot framework's native-JS web console for a new platform (e.g. OneBot), wiring OneBot message events into the unified plugin system, or safely verifying the OneBot adapter end-to-end without connecting a real Napcat/QQ client. Covers console API path conventions, multi-platform table rendering helpers, and a pure-stdlib WebSocket client harness.
---

# Xuanji Console + OneBot Verification

## Overview

Captures two reusable workflows for the Xuanji bot framework (`D:\Program\bot\xuanji`, package `dev.xuanji.*`):

1. **Console multi-platform extension** — the console is a vanilla-JS SPA at `xuanji-starter/src/main/resources/static/xuanji/console/index.html` (no framework, no build step). All backend endpoints live under `/xuanji/api/*`.
2. **OneBot adapter safe verification** — verify the reverse-WebSocket OneBot adapter and the message→plugin bridge end-to-end using a pure-stdlib Python WS client, with zero real bot/client connection (loopback only, fake `self_id`).

Trigger this skill whenever the user asks to add a console dashboard panel, connect Napcat, expose a new OneBot event, or verify the OneBot adapter in isolation.

## Critical Conventions (read first)

- **Console API path prefix**: the SPA defines `const A='/xuanji/api'`. Every NEW console backend controller MUST be mapped under `/xuanji/api` (e.g. `@RequestMapping("/xuanji/api/onebot")`). A controller mapped at `/api/onebot` makes the frontend hit `/xuanji/api/api/onebot/...` → permanent 404, silently hidden by the SPA's 404 fallback (panel shows "未启用" forever). Historical exceptions that do NOT use the prefix: `/api/plugins`, `/api/v1/websocket`, `/webhook`.
- **H2 table names are UPPERCASE** (`XUANJI_QQBOT_GROUP`). Any frontend table-name match / default-selection must use `.toLowerCase()` case-insensitive comparison or the default selection silently fails.
- **No BOM**: PowerShell `WriteAllText`/Write default UTF-8 adds a BOM (`ef bb bf`) which triggers browser quirks mode. After writing `index.html`/`.md`, strip BOM (Python: `open(f,'rb').read()` → drop first 3 bytes if `ef bb bf`). Validate JS with `node --check` on the extracted `<script>` block.
- **PowerShell `$pid` is read-only** — use another variable name (e.g. `$target`) when killing processes.
- **WebSocket client frames MUST be masked**: RFC6455 requires client→server frames to set the mask bit (`0x80 | len`) and XOR the 4-byte mask over the payload. Unmasked client frames make Tomcat return `CloseStatus[code=1002, reason=The client frame was not masked]` and drop the connection.
- **Use `conn.sock` (not `conn`/`conn.sock` wrapper) for recv/sendall** on the raw socket after `http.client` upgrade.
- The framework's `send` blocks on the OneBot API `echo` response; a test client MUST echo back the same `echo` value or `send` hangs until timeout.

## Console Extension Helpers (already in index.html)

Reuse these existing helpers instead of re-inventing:

- `getSafe(u)` — fetch that returns `null` on 404/exception (for graceful degradation of multi-platform panels).
- `dirBadge(d)` — uniform direction badge: `IN`/`OUT` get distinct colors; `LOG` → `badge-warn`; everything else → `badge-off`.
- `fillTablePicker(selId, keyword, preferred)` — case-insensitive discovery of data tables by keyword, with a default selection.
- `platLabel(n)` — infer platform from table name (`_qqbot` → "QQ 官方", `_onebot` → "OneBot").
- `renderRows(box, d, empty)` — generic column renderer.

Pattern for a new platform panel: add an `icons.*` entry, push a `navs` item, add a `case` in `render()`, write `<sectionId>HTML()` + `load<Section>()` that calls `getSafe(A+'/<platform>/status')` and degrades to "未启用" on null. For realtime event streams, consume `GET /xuanji/api/console/events` (in-memory ring buffer, 200 cap) with a 3s auto-refresh and `dirBadge()` for direction.

## OneBot Message → Plugin Bridge

`OneBotMessageHandler implements EventHandler` with `@EventMapping` covering `message.group*` / `message.private*` / `message.guild*`. Internally route by **prefix** (`raw.startsWith("message.group")`), NOT `equals`, because `OneBotEventConverter` emits `rawEventType = post_type.detail.sub_type` (e.g. `message.group.normal`). It mirrors QQ's `GroupMessageHandler`: build `GroupMessageEvent`/`PrivateMessageEvent`, `CommandRegistry.setContext(..., Bot)`, then `executeGroupMessage/executePrivateMessage`. `atBot` is detected by comparing the `At` segment's userId with `botEvent.bot().selfId()`. Plugins use the SAME `@GroupMessage`/`@PrivateMessage` annotations as QQ — write once, run on both platforms.

`OneBotController` exposes `GET /xuanji/api/onebot/status` (`{enabled, reverse{enabled,path,accessTokenSet}, forward{enabled,url}, sessions[{selfId,direction,open}], onlineCount}`) and `POST /xuanji/api/onebot/send` (`{selfId,target,type,text}`).

## Safe End-to-End Verification (no real bot)

Two pure-stdlib Python harnesses ship in `scripts/`:

- `scripts/wsclient_verify.py` — connects to the reverse WS, sends a `lifecycle` meta_event + a `@bot hello 世界` group message, and echoes back the `send_*` action `echo` so `send` completes. Expect the demo plugin `@GroupMessage hello` to reply 「你好, hello!」.
- `scripts/hold_conn.py` — keeps a connection alive 20s to verify the "online sessions" count and panel display.

Run in an isolated temp dir (never the real work dir, never with real credentials):

```bash
cp xuanji-starter/target/xuanji-starter.jar /tmp/obtest/
cd /tmp/obtest
nohup java -jar xuanji-starter.jar --server.port=18081 \
  --xuanji.onebot.enabled=true --xuanji.onebot.reverse.enabled=true \
  --xuanji.onebot.reverse.path=/onebot/ws --xuanji.onebot.reverse.access-token=verify-token-x &
python3 scripts/wsclient_verify.py
python3 scripts/hold_conn.py
# then kill the java PID and release the port
```

The harness uses `Authorization: Bearer verify-token-x` + `X-Self-ID: 10001` (both fake). It connects to `127.0.0.1` loopback only — zero real connections. After verification, kill the java process by port (not by guessing PIDs) and delete the temp dir.

## Build & Verify

- Build: `PowerShell mvn.cmd -DskipTests clean package` with `JAVA_HOME=D:\Program\Java\jdk-25` (Git Bash `mvn` sh script fails on Windows classpath — always use `mvn.cmd` via PowerShell).
- Maven logs are UTF-16LE; read with `Get-Content -Encoding Unicode` or `cat -v | sed 's/\^@//g'`.
- When running `-pl <module> test`, use `-am` (also make dependents) so the reactor reuses freshly compiled `xuanji-api` classes instead of a stale `.m2` snapshot (avoids `NoSuchMethodError: BotEvent.<init>(...10 参)`).

## References

See `references/onebot_events.md` for the full OneBot v11 event/segment/API coverage matrix and the implemented event-type constants.
