package dev.xuanji.adapter.qqbot.storage;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.adapter.qqbot.websocket.QqBotWsManager;
import dev.xuanji.core.storage.FrameworkBotRepository;
import dev.xuanji.core.storage.PlatformDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 平台的控制台数据聚合实现。
 *
 * <p>把 core 侧平台无关的聚合请求翻译成 QQ 实例库（qqbot_group / qqbot_user / qqbot_message /
 * qqbot_event）的查询，全部委托给 {@link QqBotRepository}。
 *
 * <p>本类随 {@code xuanji-adapter-qqbot} 模块一起可插拔：不引入该模块时 core 侧拿不到本实现，
 * 控制台自动退化为空数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnQqbotEnabled
public class QqPlatformDataProvider implements PlatformDataProvider {

    // ===== getBotConfig 契约键（camelCase，控制台按此读取） =====
    public static final String CFG_APP_ID = "appId";
    public static final String CFG_CLIENT_SECRET = "clientSecret";
    public static final String CFG_CONNECTION_METHOD = "connectionMethod";
    public static final String CFG_SANDBOX = "sandbox";
    public static final String CFG_STATUS = "status";
    public static final String CFG_WEBHOOK_URL = "webhookUrl";

    private final QqBotRepository repo;
    private final RobotRegistry robotRegistry;
    private final QqBotWsManager wsManager;
    private final FrameworkBotRepository frameworkBotRepository;

    @Override
    public String platform() {
        return "qqbot";
    }

    @Override
    public List<String> listInstanceIds() {
        return repo.listInstanceIds();
    }

    @Override
    public Map<String, Object> getBotConfig(String instanceId) {
        Map<String, Object> row = repo.getBotRow(instanceId);
        if (row.isEmpty()) return Map.of();
        // JDBC 列名在 H2 下为大写，统一翻译为契约定义的驼峰键
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put(CFG_APP_ID, str(row, "BOT_APPID"));
        cfg.put(CFG_CLIENT_SECRET, str(row, "BOT_CLIENTSECRET"));
        cfg.put(CFG_CONNECTION_METHOD, str(row, "CONN_MODE"));
        cfg.put(CFG_SANDBOX, bool(row.get("IS_SANDBOX")));
        cfg.put(CFG_STATUS, str(row, "STATUS"));
        cfg.put(CFG_WEBHOOK_URL, str(row, "WEBHOOK_URL"));
        return cfg;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) v = row.get(key.toLowerCase());
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean bool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s);
    }

    @Override
    public List<Map<String, Object>> listGroups(String instanceId) {
        return repo.listGroups(instanceId);
    }

    @Override
    public List<Map<String, Object>> listFriends(String instanceId) {
        return repo.listUsers(instanceId);
    }

    @Override
    public List<Map<String, Object>> listGroupMembers(String instanceId, String groupId) {
        return repo.listGroupMembers(instanceId, groupId);
    }

    @Override
    public List<Map<String, Object>> listMessages(String instanceId, String chatType, int limit) {
        return repo.listMessages(instanceId, chatType, limit);
    }

    @Override
    public List<Map<String, Object>> listMessagesByTarget(String instanceId, String chatType,
                                                          String targetId, int limit) {
        return repo.listMessagesByTarget(instanceId, chatType, targetId, limit);
    }

    @Override
    public List<Map<String, Object>> listEvents(String instanceId, int limit) {
        return repo.listEvents(instanceId, limit);
    }

    @Override
    public long countGroups(String instanceId) {
        return repo.countGroups(instanceId);
    }

    @Override
    public long countFriends(String instanceId) {
        return repo.countUsers(instanceId);
    }

    @Override
    public long countMessagesSince(String instanceId, String chatType, long sinceEpochSeconds) {
        return repo.countMessagesSince(instanceId, chatType, sinceEpochSeconds);
    }

    @Override
    public long countEventsSince(String instanceId, String eventKind, long sinceEpochSeconds) {
        List<String> qqTypes = switch (eventKind) {
            case EVT_GROUP_ADD -> List.of("GROUP_ADD_ROBOT");
            case EVT_GROUP_DEL -> List.of("GROUP_DEL_ROBOT");
            case EVT_FRIEND_ADD -> List.of("FRIEND_ADD");
            case EVT_FRIEND_DEL -> List.of("FRIEND_DEL");
            default -> List.of();
        };
        return repo.countEventsSince(instanceId, qqTypes, sinceEpochSeconds);
    }

    @Override
    public long countAllEvents(String instanceId) {
        return repo.countAllEvents(instanceId);
    }

    @Override
    public Map<String, Object> getBotInfo(String instanceId) {
        return repo.getBotInfo(instanceId);
    }

    @Override
    public String getConnectionType(String instanceId) {
        // instanceId 对 QQ = appId
        Robot robot = robotRegistry.findByAppId(instanceId);
        if (robot == null) return "";
        String mode = robot.getConnectionMethod();
        return mode == null ? "" : mode;
    }

    @Override
    public void stopBot(String instanceId) {
        Robot robot = robotRegistry.findByAppId(instanceId);
        String method = robot != null ? robot.getConnectionMethod() : null;
        String robotId = robot != null ? robot.getId() : null;
        if ("webhook".equalsIgnoreCase(method)) {
            // webhook 是被动接收，无连接可断；置 Robot.status=0 让 WebhookServiceImpl 拒绝后续回调
            log.info("[QQ] webhook 机器人停止: appId={}（已标记停用，后续回调将被拒绝）", instanceId);
            if (robotId != null) robotRegistry.setRobotStatus(robotId, 0);
        } else {
            if (robotId != null) {
                wsManager.stop(robotId);
                robotRegistry.setRobotStatus(robotId, 0);
            } else {
                wsManager.stop(instanceId); // 注册表缺失时按 appId 兜底清理残留连接
            }
        }
        // 持久化连接状态（xuanji_bot 供界面显示，qqbot_bot 供重启时判断启停）
        frameworkBotRepository.setStatus(platform(), instanceId, "OFFLINE");
        repo.updateBotStatus(instanceId, "OFFLINE");
    }

    @Override
    public void startBot(String instanceId, String envType) {
        Robot robot = robotRegistry.findByAppId(instanceId);
        String method = robot != null ? robot.getConnectionMethod() : null;
        String robotId = robot != null ? robot.getId() : null;
        if ("webhook".equalsIgnoreCase(method)) {
            // webhook 无需建立连接；恢复 Robot.status=1 使回调重新被处理
            log.info("[QQ] webhook 机器人启用: appId={}（已恢复接收回调）", instanceId);
            if (robotId != null) robotRegistry.setRobotStatus(robotId, 1);
        } else if (robotId != null) {
            wsManager.start(robotId, envType == null ? "SANDBOX" : envType);
            robotRegistry.setRobotStatus(robotId, 1);
        } else {
            log.warn("[QQ] 机器人未注册到 registry: appId={}，仅更新状态", instanceId);
        }
        frameworkBotRepository.setStatus(platform(), instanceId, "ONLINE");
        repo.updateBotStatus(instanceId, "ONLINE");
    }
}
