package dev.xuanji.adapter.onebot.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.adapter.onebot.adapter.OneBotBotManager;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.converter.OneBotEventConverter;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
import dev.xuanji.core.pipeline.BotPipeline;
import lombok.extern.slf4j.Slf4j;

/**
 * OneBot 报文分发器 — 正向/反向 WS 共用的入站处理中枢。
 *
 * <p>一条 WS 文本进来后按序判定：
 * <ol>
 *   <li>带 echo 的 <b>API 响应</b> → 交回 {@link OneBotApiService} 唤醒在途请求</li>
 *   <li>{@code post_type=meta_event} 的 <b>心跳/生命周期</b> → 仅更新在线状态，不进流水线</li>
 *   <li>其余 <b>业务事件</b> → 转 {@link BotEvent} 后送入 {@link BotPipeline}</li>
 * </ol>
 *
 * <p>这是"抽象先行"的落点：OneBot 事件与 QQ 事件汇入同一条流水线，
 * 黑名单、权限、限流、指令分发等阶段对两个平台一视同仁。
 */
@Slf4j
public class OneBotEventDispatcher {

    private final OneBotApiService api;
    private final OneBotBotManager botManager;
    private final BotPipeline pipeline;
    private final OneBotProperties props;

    public OneBotEventDispatcher(OneBotApiService api,
                                 OneBotBotManager botManager,
                                 BotPipeline pipeline,
                                 OneBotProperties props) {
        this.api = api;
        this.botManager = botManager;
        this.pipeline = pipeline;
        this.props = props;
    }

    /**
     * 处理一条入站 WS 文本。
     *
     * @param fallbackSelfId 报文内无 self_id 时使用的连接级 selfId
     */
    public void onMessage(String payload, String fallbackSelfId) {
        JsonNode raw;
        try {
            raw = Json.parse(payload);
        } catch (Exception e) {
            log.warn("[OneBot] 报文解析失败: {}", trunc(payload));
            return;
        }
        if (!raw.isObject()) {
            return;
        }

        // 1) API 响应回执
        if (OneBotEventConverter.isApiResponse(raw) && api.completeResponse(raw)) {
            return;
        }

        String selfId = raw.hasNonNull("self_id") ? raw.get("self_id").asText() : fallbackSelfId;

        // 2) 元事件：心跳与生命周期
        if (OneBotEventConverter.isMetaEvent(raw)) {
            handleMetaEvent(raw, selfId);
            return;
        }

        // 3) 自身发出的消息（部分实现会回显）
        if (props.isIgnoreSelfMessage() && "message_sent".equals(raw.path("post_type").asText(""))) {
            return;
        }

        // 4) 业务事件 → 统一流水线
        try {
            Bot bot = botManager.findOrCreate(selfId);
            BotEvent event = OneBotEventConverter.convert(bot, raw);
            if (event == null) {
                return;
            }
            log.info("[OneBot事件] type={}, raw={}, user={}, group={}, text={}",
                    event.type().fullName(), event.rawEventType(),
                    event.sender() != null ? event.sender().nickname() : "",
                    event.group() != null ? event.group().groupId() : "私聊",
                    event.message() != null ? trunc(event.message().plainText()) : "");
            pipeline.proceed(event);
        } catch (Exception e) {
            log.error("[OneBot] 事件处理异常: selfId={}, error={}", selfId, e.getMessage(), e);
        }
    }

    /** 心跳 / 生命周期：只维护在线状态，不打扰业务流水线 */
    private void handleMetaEvent(JsonNode raw, String selfId) {
        String metaType = raw.path("meta_event_type").asText("");
        if ("lifecycle".equals(metaType)) {
            String sub = raw.path("sub_type").asText("");
            log.info("[OneBot] 生命周期事件: selfId={}, sub_type={}", selfId, sub);
            if ("connect".equals(sub) || "enable".equals(sub)) {
                botManager.markOnline(selfId);
            } else if ("disable".equals(sub)) {
                botManager.markOffline(selfId);
            }
        } else if ("heartbeat".equals(metaType)) {
            boolean online = raw.path("status").path("online").asBoolean(true);
            boolean good = raw.path("status").path("good").asBoolean(true);
            if (online && good) {
                botManager.markOnline(selfId);
            } else {
                log.warn("[OneBot] 心跳异常: selfId={}, online={}, good={}", selfId, online, good);
                botManager.markOffline(selfId);
            }
        }
    }

    private static String trunc(String s) {
        if (s == null) return "";
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}
