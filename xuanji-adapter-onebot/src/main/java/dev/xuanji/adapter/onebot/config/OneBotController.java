package dev.xuanji.adapter.onebot.config;

import dev.xuanji.adapter.onebot.converter.OneBotMessageConverter;
import dev.xuanji.adapter.onebot.sender.OneBotMessageSenderImpl;
import dev.xuanji.adapter.onebot.session.OneBotSession;
import dev.xuanji.adapter.onebot.session.OneBotSessionRegistry;
import dev.xuanji.api.message.MessageChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OneBot 适配器控制台接口 —— 状态查看 + 主动发消息（供控制台 OneBot 板块交互）。
 *
 * <p>本类带 {@code @ConditionalOnProperty(xuanji.onebot.enabled=true)}，
 * 因此 OneBot 关闭（默认）时根本不被组件扫描、不会因缺少 OneBotProperties Bean 而启动崩溃；
 * 启用时由 Spring 正常组件扫描并自动装配依赖，端点照常生效，控制台前端对缺失端点做 404 优雅降级。
 */
@RestController
@ConditionalOnProperty(prefix = "xuanji.onebot", name = "enabled", havingValue = "true")
@RequestMapping("/xuanji/api/onebot")
public class OneBotController {

    private final OneBotProperties props;
    private final OneBotSessionRegistry registry;
    private final OneBotMessageSenderImpl sender;

    public OneBotController(OneBotProperties props, OneBotSessionRegistry registry,
                            OneBotMessageSenderImpl sender) {
        this.props = props;
        this.registry = registry;
        this.sender = sender;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);

        Map<String, Object> rev = new LinkedHashMap<>();
        rev.put("enabled", props.getReverse().isEnabled());
        rev.put("path", props.getReverse().getPath());
        rev.put("accessTokenSet", props.getReverse().getAccessToken() != null
                && !props.getReverse().getAccessToken().isBlank());
        m.put("reverse", rev);

        Map<String, Object> fwd = new LinkedHashMap<>();
        fwd.put("enabled", props.getForward().isEnabled());
        fwd.put("url", props.getForward().getUrl());
        m.put("forward", fwd);

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (OneBotSession s : registry.all()) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("selfId", s.selfId());
            sm.put("direction", s.direction());
            sm.put("open", s.isOpen());
            sessions.add(sm);
        }
        m.put("sessions", sessions);
        m.put("onlineCount", registry.onlineCount());
        return m;
    }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, String> body) {
        String selfId = body.get("selfId");
        String target = body.get("target");
        String type = body.getOrDefault("type", "group");
        String text = body.getOrDefault("text", "");
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.node.ArrayNode segments =
                    OneBotMessageConverter.toSegments(MessageChain.text(text));
            if ("group".equals(type)) {
                sender.sendGroup(selfId, target, segments);
            } else {
                sender.sendPrivate(selfId, target, segments);
            }
            result.put("ok", true);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}