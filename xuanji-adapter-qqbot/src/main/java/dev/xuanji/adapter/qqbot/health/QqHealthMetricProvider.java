package dev.xuanji.adapter.qqbot.health;

import dev.xuanji.adapter.qqbot.api.QqApiService;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.storage.HealthMetricProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QQ 平台健康指标探针 — 控制台 /health 监控页数据源。
 *
 * <p>提供：API 熔断快照、注册机器人总数、累计群/好友/消息数（per-bot 汇总）、WS 客户端数。
 * 只读查询，不触碰发送/连接逻辑。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnQqbotEnabled
public class QqHealthMetricProvider implements HealthMetricProvider {

    private final QqApiService qqApiService;
    private final QqBotRepository qqBotRepository;
    private final QqBotWsManager wsManager;

    @Override
    public String platform() { return "qqbot"; }

    @Override
    public Map<String, Object> healthMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("circuitBreaker", qqApiService.getCircuitBreakerSnapshot());
        try {
            List<String> ids = qqBotRepository.listInstanceIds();
            m.put("registeredBots", ids.size());
            long groups = 0, friends = 0, messages = 0;
            for (String id : ids) {
                groups += qqBotRepository.countGroups(id);
                friends += qqBotRepository.countUsers(id);
                messages += qqBotRepository.countMessagesSince(id, "group", 0L)
                        + qqBotRepository.countMessagesSince(id, "c2c", 0L);
            }
            m.put("groups", groups);
            m.put("friends", friends);
            m.put("messages", messages);
        } catch (Exception e) {
            m.put("statsError", e.getMessage());
        }
        m.put("wsClients", wsManager.getAllStatus().size());
        return m;
    }
}
