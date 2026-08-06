/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.fasterxml.jackson.databind.node.ArrayNode
 *  dev.xuanji.api.message.MessageChain
 *  dev.xuanji.core.concurrent.BotOutboundExecutor
 *  org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 *  org.springframework.web.bind.annotation.GetMapping
 *  org.springframework.web.bind.annotation.PostMapping
 *  org.springframework.web.bind.annotation.RequestBody
 *  org.springframework.web.bind.annotation.RequestMapping
 *  org.springframework.web.bind.annotation.RestController
 */
package dev.xuanji.adapter.onebot.config;

import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.xuanji.adapter.onebot.config.OneBotProperties;
import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.core.concurrent.BotOutboundExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix="xuanji.onebot", name={"enabled"}, havingValue="true")
@RequestMapping(value={"/xuanji/api/onebot"})
public class OneBotController {
    private final OneBotProperties props;
    private final OneBotSessionRegistry registry;
    private final OneBotMessageSenderImpl sender;
    private final BotOutboundExecutor outbound;

    public OneBotController(OneBotProperties props, OneBotSessionRegistry registry, OneBotMessageSenderImpl sender, BotOutboundExecutor outbound) {
        this.props = props;
        this.registry = registry;
        this.sender = sender;
        this.outbound = outbound;
    }

    @GetMapping(value={"/status"})
    public Map<String, Object> status() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("enabled", true);
        LinkedHashMap<String, Object> rev = new LinkedHashMap<String, Object>();
        rev.put("enabled", this.props.getReverse().isEnabled());
        rev.put("path", this.props.getReverse().getPath());
        rev.put("accessTokenSet", this.props.getReverse().getAccessToken() != null && !this.props.getReverse().getAccessToken().isBlank());
        m.put("reverse", rev);
        LinkedHashMap<String, Object> fwd = new LinkedHashMap<String, Object>();
        fwd.put("enabled", this.props.getForward().isEnabled());
        fwd.put("url", this.props.getForward().getUrl());
        m.put("forward", fwd);
        ArrayList sessions = new ArrayList();
        for (OneBotSession s : this.registry.all()) {
            LinkedHashMap<String, Object> sm = new LinkedHashMap<String, Object>();
            sm.put("selfId", s.selfId());
            sm.put("direction", s.direction());
            sm.put("open", s.isOpen());
            sessions.add(sm);
        }
        m.put("sessions", sessions);
        m.put("onlineCount", this.registry.onlineCount());
        return m;
    }

    @PostMapping(value={"/send"})
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String selfId = body.get("selfId");
        String target = body.get("target");
        String type = body.getOrDefault("type", "group");
        String text = body.getOrDefault("text", "");
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            ArrayNode segments = OneBotMessageConverter.toSegments(MessageChain.text((String)text));
            this.outbound.awaitPace(selfId);
            if ("group".equals(type)) {
                this.sender.sendGroup(selfId, target, segments);
            } else {
                this.sender.sendPrivate(selfId, target, segments);
            }
            result.put("ok", true);
        }
        catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}

