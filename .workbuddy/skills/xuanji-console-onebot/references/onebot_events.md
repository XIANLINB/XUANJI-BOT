# OneBot v11 Event / Segment / API Coverage (Xuanji adapter)

Reference for what the `xuanji-adapter-onebot` module implements and how events map into the unified `BotEvent` system. Sources: Napcat API docs + Shiro OneBot v11 docs.

## Event categories → EventType constants (`OneBotEventTypes`)

All emitted `rawEventType` follow `post_type.detail.sub_type` (e.g. `message.group.normal`). Handlers should route by **prefix** (`startsWith("message.group")`), not `equals`.

### message (3 subtypes routed to CommandRegistry)
| rawEventType | Mapped EventType | Bridges to |
|---|---|---|
| `message.group.normal` / `.anonymous` / `.notice` | `MESSAGE_GROUP` | `@GroupMessage` |
| `message.private.friend` / `.group` / `.other` | `MESSAGE_PRIVATE` | `@PrivateMessage` |
| `message.guild.normal` | `MESSAGE_GUILD` | guild handler (not yet routed to a plugin annotation) |

### notice (13 subtypes)
| sub_type | EventType | Notes |
|---|---|---|
| `group_upload` | `NOTICE_GROUP_UPLOAD` | |
| `group_admin` (set/unset) | `NOTICE_GROUP_ADMIN` | |
| `group_decrease` (leave/kick/kick_me) | `NOTICE_GROUP_DECREASE` | `operator_id` kept in platformData |
| `group_increase` (add/invite/approve) | `NOTICE_GROUP_INCREASE` | |
| `group_ban` (ban/lift_ban) | `NOTICE_GROUP_BAN` | |
| `group_card` | `NOTICE_GROUP_CARD` | |
| `group_recall` | `NOTICE_GROUP_RECALL` | |
| `friend_add` | `NOTICE_FRIEND_ADD` | |
| `friend_recall` | `NOTICE_FRIEND_RECALL` | |
| `offline_file` | `NOTICE_OFFLINE_FILE` | |
| `notify.poke` | `NOTIFY_POKE` | |
| `notify.lucky_king` | `NOTIFY_LUCKY_KING` | |
| `notify.honor` | `NOTIFY_HONOR` | |

### request (3 subtypes)
| sub_type | EventType | |
|---|---|---|
| `friend` | `REQUEST_FRIEND_ADD` | approve/reject via `OneBotXjBot` |
| `group.add` | `REQUEST_GROUP_ADD` | |
| `group.invite` | `REQUEST_GROUP_INVITE` | |

### meta_event (2 subtypes)
| sub_type | EventType | |
|---|---|---|
| `lifecycle` | `META_LIFECYCLE` | connect/disconnect |
| `heartbeat` | `META_HEARTBEAT` | status interval |

Unmatched events fall back to `onebot/<post_type>/<sub_type>` namespace (never dropped).

## Message segments (OneBotMessageConverter)

**Receive (OneBot → MessageChain):** text, image, face, at, reply, markdown (Napcat → Markdown element), rps, dice, shake, poke, anonymous, share, contact, location, music, node, xml, mface. Unknown/extended segments pass through transparently (restored verbatim on send, round-trip lossless).

**Send (MessageChain → OneBot):** Markdown → native `markdown` segment (Napcat-compatible, no text downgrade). `reply` segment injected for `reply(...)` calls.

## Bot/Group-management APIs (OneBotXjBot)
- Friend/group request: `approve/rejectFriendRequest`, `approve/rejectGroupAddRequest`, `approve/rejectGroupInvite`
- Group moderation: `kickGroupMember`, `ban/unbanGroupMember`, `setGroupWholeBan`, `setGroupAdmin`, `setGroupCard`, `setGroupName`, `leaveGroup`
- Misc: `deleteFriend`, `sendLike`

All require the current event's `selfId`/`groupId`; non-group actions throw `IllegalStateException`.

## Key implementation classes (xuanji-adapter-onebot)
- `OneBotEventConverter` — 31-case `mapEventType` switch (穷尽 v11 全量)
- `OneBotMessageConverter` — bidirectional segment↔MessageChain
- `OneBotApiService` — echo correlation (`CompletableFuture.get` timeout, `failAllPending` on disconnect)
- `OneBotEventDispatcher` — API response → meta → ignore-self → business event into `BotPipeline`
- `OneBotWsServer` — reverse WS server (Bearer / `?access_token` auth, `X-Self-ID`)
- `OneBotWsClient` — forward WS client (virtual-thread reconnect)
- `OneBotMessageHandler` — `@EventMapping` message router → `CommandRegistry`
- `OneBotController` — `GET /xuanji/api/onebot/status`, `POST /xuanji/api/onebot/send`
- `OneBotAutoConfiguration` — sole assembly entry; `enabled=false` → zero beans

## Two Bot types — do NOT confuse
- `dev.xuanji.sdk.bot.Bot` (abstract) — plugin facade (reply/send/群管). Pass to `CommandRegistry.setContext`.
- `dev.xuanji.api.adapter.Bot` (record) — `BotEvent.bot()` metadata (id/platform/selfId/status/capabilities). Construct `new Bot("onebot:10001:42","onebot","99999",Bot.Status.ONLINE,Set.of())` for test events.
