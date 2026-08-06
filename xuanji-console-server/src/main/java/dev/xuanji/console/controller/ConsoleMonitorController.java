package dev.xuanji.console.controller;

import dev.xuanji.console.service.ConsoleQueryService;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.config.ConfigService;
import dev.xuanji.core.pipeline.BotPipeline;
import dev.xuanji.core.storage.ConnectionStatusProvider;
import dev.xuanji.core.storage.HealthMetricProvider;
import dev.xuanji.core.storage.PlatformDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 控制台 · 仪表盘 / 运行健康 / 运行时配置（只读查询 + 配置写入）。
 */
@Slf4j
@RestController
@RequestMapping("/xuanji/api/console")
public class ConsoleMonitorController {

    private final ConsoleQueryService queryService;
    private final ConfigService configService;
    private final CommandRegistry commandRegistry;
    private final BotPipeline botPipeline;
    private final ObjectProvider<HealthMetricProvider> healthProviders;
    private final ObjectProvider<ConnectionStatusProvider> connProviders;

    public ConsoleMonitorController(ConsoleQueryService queryService,
                                    ConfigService configService,
                                    CommandRegistry commandRegistry,
                                    BotPipeline botPipeline,
                                    ObjectProvider<HealthMetricProvider> healthProviders,
                                    ObjectProvider<ConnectionStatusProvider> connProviders) {
        this.queryService = queryService;
        this.configService = configService;
        this.commandRegistry = commandRegistry;
        this.botPipeline = botPipeline;
        this.healthProviders = healthProviders;
        this.connProviders = connProviders;
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
        return m;
    }

    // ═══════════════════ 运行健康监控 ═════════════════

    /** 运行健康快照：插件超时、去重、Pipeline 慢阶段、各平台熔断与连接状态。只读，不触碰生命线。 */
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
        return m;
    }

    // ═══════════════════ 运行时配置 ═════════════════

    /** 返回全局 + 每机器人配置快照（供设置页）。 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return configService.getConfigView();
    }

    /** 更新全局 KV 设置（body: {k: v, ...}）。 */
    @PutMapping("/config/global")
    public Map<String, Object> putGlobal(@RequestBody Map<String, String> body) {
        body.forEach((k, v) -> configService.setGlobal(k, v == null ? "" : v));
        return Map.of("status", "ok");
    }

    /** 更新某机器人配置（body: 字段映射；botKey 自动归一为 appId）。 */
    @PutMapping("/config/bot/{botKey}")
    public Map<String, Object> putBot(@PathVariable String botKey, @RequestBody Map<String, String> body) {
        configService.setBotConfig(botKey, body);
        return Map.of("status", "ok");
    }

    /** 更新某机器人某群的配置（三级粒度：全局 / bot / 群）。 */
    @PutMapping("/config/group/{botKey}/{groupId}")
    public Map<String, Object> putGroup(@PathVariable String botKey, @PathVariable String groupId,
                                        @RequestBody Map<String, String> body) {
        body.forEach((k, v) -> configService.setGroupConfig(botKey, groupId, k, v == null ? "" : v));
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
