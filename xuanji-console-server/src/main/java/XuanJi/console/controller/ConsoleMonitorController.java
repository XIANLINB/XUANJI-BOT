package XuanJi.console.controller;

import XuanJi.console.service.AuditService;
import XuanJi.console.service.ConsoleQueryService;
import XuanJi.core.command.CommandRegistry;
import XuanJi.core.config.ConfigService;
import XuanJi.core.pipeline.BotPipeline;
import XuanJi.core.storage.ConnectionStatusProvider;
import XuanJi.core.storage.HealthMetricProvider;
import XuanJi.core.storage.PlatformDataProvider;
import XuanJi.core.web.XuanJiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台 · 仪表盘 / 运行健康 / 运行时配置（只读查询 + 配置写入）。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console")
public class ConsoleMonitorController {

    private final ConsoleQueryService queryService;
    private final ConfigService configService;
    private final CommandRegistry commandRegistry;
    private final BotPipeline botPipeline;
    private final ObjectProvider<HealthMetricProvider> healthProviders;
    private final ObjectProvider<ConnectionStatusProvider> connProviders;
    private final AuditService auditService;
    private final XuanJi.console.service.HealthAlarmService healthAlarmService;
    private final XuanJi.core.plugin.XuanJiPluginManager pluginManager;

    public ConsoleMonitorController(ConsoleQueryService queryService,
                                    ConfigService configService,
                                    CommandRegistry commandRegistry,
                                    BotPipeline botPipeline,
                                    ObjectProvider<HealthMetricProvider> healthProviders,
                                    ObjectProvider<ConnectionStatusProvider> connProviders,
                                    AuditService auditService,
                                    XuanJi.console.service.HealthAlarmService healthAlarmService,
                                    XuanJi.core.plugin.XuanJiPluginManager pluginManager) {
        this.queryService = queryService;
        this.configService = configService;
        this.commandRegistry = commandRegistry;
        this.botPipeline = botPipeline;
        this.healthProviders = healthProviders;
        this.connProviders = connProviders;
        this.auditService = auditService;
        this.healthAlarmService = healthAlarmService;
        this.pluginManager = pluginManager;
    }

    // ═════════════════ 仪表盘 ═══════════════════

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("botsOnline", queryService.qInt("SELECT COUNT(*) FROM xuanji_bot WHERE status='ONLINE'"));
        m.put("botsTotal", queryService.qInt("SELECT COUNT(*) FROM xuanji_bot"));

        long since = ConsoleQueryService.todayStartEpochSeconds();
        long groups = 0, friends = 0, gAdd = 0, gDel = 0, fAdd = 0, fDel = 0, gMsg = 0, cMsg = 0;
        long msgTotal = 0, evtTotal = 0;
        for (ConsoleQueryService.BotRef b : queryService.botRefs()) {
            PlatformDataProvider p = queryService.providerFor(b.platform());
            if (p == null) continue;
            groups += p.countGroups(b.instanceId());
            friends += p.countFriends(b.instanceId());
            gAdd += p.countEventsSince(b.instanceId(), PlatformDataProvider.EVT_GROUP_ADD, since);
            gDel += p.countEventsSince(b.instanceId(), PlatformDataProvider.EVT_GROUP_DEL, since);
            fAdd += p.countEventsSince(b.instanceId(), PlatformDataProvider.EVT_FRIEND_ADD, since);
            fDel += p.countEventsSince(b.instanceId(), PlatformDataProvider.EVT_FRIEND_DEL, since);
            gMsg += p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_GROUP, since);
            cMsg += p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_C2C, since);
            // 全时段累计（since=0）
            msgTotal += p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_GROUP, 0L)
                    + p.countMessagesSince(b.instanceId(), PlatformDataProvider.CHAT_C2C, 0L);
            evtTotal += p.countAllEvents(b.instanceId());
        }
        m.put("groupsTotal", groups);
        m.put("friendsTotal", friends);
        m.put("todayGroupAdd", gAdd);
        m.put("todayGroupDel", gDel);
        m.put("todayFriendAdd", fAdd);
        m.put("todayFriendDel", fDel);
        m.put("todayGroupMessages", gMsg);
        m.put("todayC2cMessages", cMsg);
        m.put("messagesTotal", msgTotal);
        m.put("eventsTotal", evtTotal);
        // 插件执行 / 消息去重（从健康监控取，Dashboard 复用）
        try {
            m.put("plugins", commandRegistry.getStats());
            m.put("dedup", botPipeline.getDedupStats());
        } catch (Exception ignored) {
            m.put("plugins", Map.of());
            m.put("dedup", Map.of());
        }
        // 已加载插件数（全部插件，含停用的）
        try {
            m.put("pluginsLoaded", pluginManager.listPlugins().size());
        } catch (Exception e) {
            m.put("pluginsLoaded", 0);
        }
        // 命令数（按 命令名|插件|方法 去重后的实际命令数，来自命令管理页同一数据源）
        try {
            m.put("commandCount", commandRegistry.listCommands().size());
        } catch (Exception e) {
            m.put("commandCount", 0);
        }
        return m;
    }

    // ═══════════════════ 运行健康监控 ═════════════════

    /** 运行健康快照：插件超时、去重、Pipeline 慢阶段、各平台熔断与连接状态。只读，不触碰生命线。
     * 同时检测当前异常（熔断打开/断连/慢阶段/QPS 突增）并持久化到 xuanji_health_alarm。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plugins", commandRegistry.getStats());
        m.put("dedup", botPipeline.getDedupStats());
        m.put("pipelineSlowStages", botPipeline.getSlowStageCounts());

        Map<String, Object> platforms = new LinkedHashMap<>();
        for (HealthMetricProvider p : healthProviders) {
            try { platforms.put(p.platform(), p.healthMetrics()); }
            catch (Exception ignored) {}
        }
        m.put("platforms", platforms);

        Map<String, Object> conns = new LinkedHashMap<>();
        for (ConnectionStatusProvider p : connProviders) {
            try { conns.put(p.platform(), p.connections()); }
            catch (Exception ignored) {}
        }
        m.put("connections", conns);

        detectAndRecordAlarms(m);
        return m;
    }

    /** 检测当前异常并落库（5 分钟窗口去重，避免 30s 轮询刷屏）。 */
    private void detectAndRecordAlarms(Map<String, Object> health) {
        try {
            // 1. 熔断打开
            @SuppressWarnings("unchecked")
            Map<String, Object> platforms = (Map<String, Object>) health.getOrDefault("platforms", Map.of());
            for (Map.Entry<String, Object> e : platforms.entrySet()) {
                Object cb = e.getValue() instanceof Map<?, ?> mm ? mm.get("circuitBreaker") : null;
                java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
                if (cb instanceof java.util.List<?> l) l.forEach(x -> { if (x instanceof Map<?, ?> mm2) list.add((Map<String, Object>) mm2); });
                else if (cb instanceof Map<?, ?> mm3) list.add((Map<String, Object>) mm3);
                for (Map<String, Object> r : list) {
                    if ("OPEN".equalsIgnoreCase(String.valueOf(r.get("state")))) {
                        healthAlarmService.record("CIRCUIT_OPEN", "ERROR",
                                "熔断打开 platform=" + e.getKey() + " appId=" + r.get("appId")
                                        + " 连续失败=" + r.get("consecutiveFailures"));
                    }
                }
            }

            // 2. WS 断连 / 重连中（非 CONNECTED 且非 CLOSED）
            @SuppressWarnings("unchecked")
            Map<String, Object> conns = (Map<String, Object>) health.getOrDefault("connections", Map.of());
            for (Map.Entry<String, Object> e : conns.entrySet()) {
                Object sessions = e.getValue() instanceof Map<?, ?> mm ? mm.get("sessions") : e.getValue();
                if (!(sessions instanceof java.util.List<?> l)) continue;
                for (Object x : l) {
                    if (!(x instanceof Map<?, ?> s)) continue;
                    String state = String.valueOf(s.get("state"));
                    if (!"CONNECTED".equalsIgnoreCase(state) && !"CLOSED".equalsIgnoreCase(state)) {
                        healthAlarmService.record("WS_DISCONNECT", "WARN",
                                "WS 连接异常 platform=" + e.getKey() + " robotId=" + s.get("robotId")
                                        + " 状态=" + state);
                    }
                }
            }

            // 3. Pipeline 慢阶段
            @SuppressWarnings("unchecked")
            Map<String, Object> slow = (Map<String, Object>) health.getOrDefault("pipelineSlowStages", Map.of());
            for (Map.Entry<String, Object> e : slow.entrySet()) {
                long cnt = e.getValue() instanceof Number n ? n.longValue() : 0;
                if (cnt > 0) {
                    healthAlarmService.record("SLOW_STAGE", "WARN", "Pipeline 慢阶段 " + e.getKey() + " 超 100ms " + cnt + " 次");
                }
            }

            // 4. QPS 突增：当前 QPS > 平均 3 倍 且 > 15（防噪音）
            try {
                double cur = XuanJi.core.metric.QpsMeter.current();
                double avg = XuanJi.core.metric.QpsMeter.avg(60);
                if (cur > 15 && cur > avg * 3) {
                    healthAlarmService.record("QPS_SPIKE", "WARN",
                            "QPS 突增 当前=" + Math.round(cur) + " 平均=" + Math.round(avg));
                }
            } catch (Exception ignored) { /* QPS 检测失败不影响健康返回 */ }
        } catch (Exception e) {
            log.debug("[Health] 异常检测失败（可忽略）: {}", e.getMessage());
        }
    }

    /** 健康异常历史记录（持久化，最多 200 条）。 */
    @GetMapping("/health/alarms")
    public Map<String, Object> healthAlarms(@RequestParam(defaultValue = "50") int limit) {
        java.util.List<Map<String, Object>> rows = healthAlarmService.list(limit);
        return Map.of("count", rows.size(), "rows", rows);
    }

    // ═══════════════════ 运行时配置 ═════════════════

    /** 返回全局 + 每机器人配置快照（供设置页）。 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return configService.getConfigView();
    }

    /** 命令清单（命令管理页数据源）：命令名/插件/方法/作用域/权限/限流。 */
    @GetMapping("/commands")
    public java.util.List<Map<String, Object>> commands() {
        return commandRegistry.listCommands();
    }

    /** 更新全局 KV 设置（body: {k: v, ...}）。 */
    @PutMapping("/config/global")
    public Map<String, Object> putGlobal(@RequestBody Map<String, String> body,
                                         jakarta.servlet.http.HttpServletRequest req) {
        body.forEach((k, v) -> configService.setGlobal(k, v == null ? "" : v));
        auditService.record("SETTINGS_UPDATE", "全局设置更新: " + String.join(", ", body.keySet()), req);
        return Map.of("status", "ok");
    }

    /** 更新某机器人配置（body: 字段映射；botKey 自动归一为 appId）。 */
    @PutMapping("/config/bot/{botKey}")
    public Map<String, Object> putBot(@PathVariable String botKey, @RequestBody Map<String, String> body,
                                      jakarta.servlet.http.HttpServletRequest req) {
        configService.setBotConfig(botKey, body);
        auditService.record("BOT_CONFIG_UPDATE", "bot=" + botKey + " 配置更新: " + String.join(", ", body.keySet()), req);
        return Map.of("status", "ok");
    }

    /** 更新某机器人某群的配置（三级粒度：全局 / bot / 群）。 */
    @PutMapping("/config/group/{botKey}/{groupId}")
    public Map<String, Object> putGroup(@PathVariable String botKey, @PathVariable String groupId,
                                        @RequestBody Map<String, String> body,
                                        jakarta.servlet.http.HttpServletRequest req) {
        body.forEach((k, v) -> configService.setGroupConfig(botKey, groupId, k, v == null ? "" : v));
        auditService.record("GROUP_CONFIG_UPDATE", "bot=" + botKey + " group=" + groupId
                + " 配置更新: " + String.join(", ", body.keySet()), req);
        return Map.of("status", "ok");
    }

    /** 重置单键（删除该键，回到默认值；用于「一键重置」按钮）。 */
    @DeleteMapping("/config/{scope}/{botKey}/{key}")
    public Map<String, Object> deleteKey(@PathVariable String scope,
                                         @PathVariable String botKey,
                                         @PathVariable String key,
                                         @RequestParam(required = false) String groupId) {
        configService.deleteKey(scope, botKey, groupId, key);
        return Map.of("status", "ok");
    }
}
