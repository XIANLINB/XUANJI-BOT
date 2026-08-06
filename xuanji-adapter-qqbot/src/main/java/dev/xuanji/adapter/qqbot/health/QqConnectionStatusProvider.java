package dev.xuanji.adapter.qqbot.health;

import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.storage.ConnectionStatusProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * QQ 平台连接状态 SPI — 控制台「运行健康」页 WebSocket 连接状态数据源。
 *
 * <p>数据来自 {@link QqBotWsManager#getAllStatus()}（各 bot 的 WS 客户端 state/running/
 * totalEvents/totalReconnects），并汇总在线数。事故恢复时该实现曾缺失，导致
 * {@code /console/health} 的 connections 恒空、前端健康页无连接状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnQqbotEnabled
public class QqConnectionStatusProvider implements ConnectionStatusProvider {

    private final QqBotWsManager wsManager;

    @Override
    public String platform() { return "qqbot"; }

    @Override
    public Map<String, Object> connections() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> all = wsManager.getAllStatus();
        // 连接成功判定：READY（鉴权完成）或 CONNECTED（已连上）都算在线
        long online = all.stream()
                .filter(s -> {
                    String st = String.valueOf(s.get("state"));
                    return "READY".equalsIgnoreCase(st) || "CONNECTED".equalsIgnoreCase(st);
                })
                .count();
        m.put("total", all.size());
        m.put("online", online);
        m.put("sessions", all);
        return m;
    }
}
