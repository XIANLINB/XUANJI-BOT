/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.JsonNode
 *  dev.xuanji.api.adapter.Bot
 *  dev.xuanji.api.event.BotEvent
 *  dev.xuanji.api.json.Json
 *  dev.xuanji.core.pipeline.BotPipeline
 *  lombok.Generated
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package dev.xuanji.adapter.onebot.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import dev.xuanji.adapter.onebot.adapter.OneBotBotManager;
import dev.xuanji.adapter.onebot.api.OneBotApiService;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.converter.OneBotEventConverter;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.adapter.Bot;
import dev.xuanji.api.event.BotEvent;
import dev.xuanji.api.json.Json;
import dev.xuanji.core.pipeline.BotPipeline;
import lombok.Generated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OneBotEventDispatcher {
    @Generated
    private static final Logger log = LoggerFactory.getLogger(OneBotEventDispatcher.class);
    private final OneBotApiService api;
    private final OneBotBotManager botManager;
    private final BotPipeline pipeline;
    private final OneBotProperties props;
    private final OneBotSessionRegistry registry;

    public OneBotEventDispatcher(OneBotApiService api, OneBotBotManager botManager, BotPipeline pipeline, OneBotProperties props, OneBotSessionRegistry registry) {
        this.api = api;
        this.botManager = botManager;
        this.pipeline = pipeline;
        this.props = props;
        this.registry = registry;
    }

    public void onMessage(String payload, String fallbackSelfId) {
        this.onMessage(payload, fallbackSelfId, null);
    }

    public void onMessage(String payload, String fallbackSelfId, OneBotSession session) {
        String selfId;
        JsonNode raw;
        try {
            raw = Json.parse((String)payload);
        }
        catch (Exception e) {
            log.warn("[OneBot] \u62a5\u6587\u89e3\u6790\u5931\u8d25: {}", (Object)OneBotEventDispatcher.trunc(payload));
            return;
        }
        if (!raw.isObject()) {
            return;
        }
        if (OneBotEventConverter.isApiResponse(raw) && this.api.completeResponse(raw)) {
            return;
        }
        String string = selfId = raw.hasNonNull("self_id") ? raw.get("self_id").asText() : fallbackSelfId;
        if (session != null && this.registry != null) {
            this.registry.rebind(session, selfId);
        }
        if (OneBotEventConverter.isMetaEvent(raw)) {
            this.handleMetaEvent(raw, selfId);
            return;
        }
        if (this.props.isIgnoreSelfMessage() && "message_sent".equals(raw.path("post_type").asText(""))) {
            return;
        }
        try {
            Bot bot = this.botManager.findOrCreate(selfId);
            BotEvent event = OneBotEventConverter.convert(bot, raw);
            if (event == null) {
                return;
            }
            log.info("[OneBot\u4e8b\u4ef6] type={}, raw={}, user={}, group={}, text={}", new Object[]{event.type().fullName(), event.rawEventType(), event.sender() != null ? event.sender().nickname() : "", event.group() != null ? event.group().groupId() : "\u79c1\u804a", event.message() != null ? OneBotEventDispatcher.trunc(event.message().plainText()) : ""});
            this.pipeline.proceed(event);
        }
        catch (Exception e) {
            log.error("[OneBot] \u4e8b\u4ef6\u5904\u7406\u5f02\u5e38: selfId={}, error={}", new Object[]{selfId, e.getMessage(), e});
        }
    }

    private void handleMetaEvent(JsonNode raw, String selfId) {
        String metaType = raw.path("meta_event_type").asText("");
        if ("lifecycle".equals(metaType)) {
            String sub = raw.path("sub_type").asText("");
            log.info("[OneBot] \u751f\u547d\u5468\u671f\u4e8b\u4ef6: selfId={}, sub_type={}", (Object)selfId, (Object)sub);
            if ("connect".equals(sub) || "enable".equals(sub)) {
                this.botManager.markOnline(selfId);
            } else if ("disable".equals(sub)) {
                this.botManager.markOffline(selfId);
            }
        } else if ("heartbeat".equals(metaType)) {
            boolean online = raw.path("status").path("online").asBoolean(true);
            boolean good = raw.path("status").path("good").asBoolean(true);
            if (online && good) {
                this.botManager.markOnline(selfId);
            } else {
                log.warn("[OneBot] \u5fc3\u8df3\u5f02\u5e38: selfId={}, online={}, good={}", new Object[]{selfId, online, good});
                this.botManager.markOffline(selfId);
            }
        }
    }

    private static String trunc(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 117) + "..." : s;
    }
}

