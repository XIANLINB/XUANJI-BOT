package dev.xuanji.adapter.qqbot.registry;

import dev.xuanji.adapter.qqbot.model.Robot;
import dev.xuanji.adapter.qqbot.model.RobotEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人注册表 — 内存中管理所有机器人配置
 *
 * <p>替代频繁的数据库查询，提供机器人信息的快速查找。
 * 在应用启动时将数据库中的机器人和环境配置加载到内存中。
 *
 * <h3>数据结构</h3>
 * <ul>
 *   <li><b>robots</b> — robotId -> Robot 映射，存储机器人基本信息</li>
 *   <li><b>environments</b> — "robotId:envType" -> RobotEnvironment 映射，存储环境配置</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link dev.xuanji.adapter.qqbot.webhook.WebhookController} — 查找机器人和环境配置</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.webhook.SignatureVerifier} — 获取密钥进行签名验证</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.api.QqApiService} — 获取凭证调用 API</li>
 *   <li>{@link dev.xuanji.adapter.qqbot.websocket.QqBotWsManager} — 获取凭证建立连接</li>
 * </ul>
 *
 * <h3>线程安全性</h3>
 * <p>使用 {@link ConcurrentHashMap} 存储数据，支持并发读写。
 * 注册操作通常在启动时完成，运行时以读操作为主。
 *
 * @see Robot            机器人实体
 * @see RobotEnvironment 环境配置实体
 */
@Slf4j
@Component
public class RobotRegistry {

    /**
     * 机器人信息映射表
     * <p>key = robotId，value = Robot 实体
     */
    private final ConcurrentHashMap<String, Robot> robots = new ConcurrentHashMap<>();

    /**
     * 环境配置映射表
     * <p>key = "robotId:envType"，value = RobotEnvironment 实体
     */
    private final ConcurrentHashMap<String, RobotEnvironment> environments = new ConcurrentHashMap<>();

    /**
     * 注册机器人
     *
     * @param robot 机器人实体（包含 id、appId、appSecret 等）
     */
    public void registerRobot(Robot robot) {
        robots.put(robot.getId(), robot);
        log.info("[机器人注册表] 注册机器人: id={}, appId={}, name={}",
                robot.getId(), robot.getAppId(), robot.getRobotName());
    }

    /**
     * 注册环境配置
     *
     * @param env 环境配置实体（包含 robotId、envType、密钥等）
     */
    public void registerEnvironment(RobotEnvironment env) {
        String key = env.getRobotId() + ":" + env.getEnvType();
        environments.put(key, env);
        log.info("[机器人注册表] 注册环境: robotId={}, env={}, connectMode={}",
                env.getRobotId(), env.getEnvType(), env.getConnectMode());
    }

    /**
     * 获取机器人
     *
     * @param robotId 机器人 ID
     * @return Robot 实体，不存在返回 null
     */
    public Robot getRobot(String robotId) {
        return robots.get(robotId);
    }

    /** 按 appId 查找 Robot */
    public Robot findByAppId(String appId) {
        for (Robot r : robots.values()) {
            if (appId.equals(r.getAppId())) return r;
        }
        return null;
    }

    /**
     * 获取环境配置
     *
     * @param robotId 机器人 ID
     * @param envType 环境类型（SANDBOX / PRODUCTION）
     * @return RobotEnvironment 实体，不存在返回 null
     */
    public RobotEnvironment getEnvironment(String robotId, String envType) {
        return environments.get(robotId + ":" + envType);
    }

    /**
     * 获取机器人的当前激活环境配置
     *
     * <p>根据机器人的 activeEnv 字段确定使用哪个环境。
     * 如果 activeEnv 为空，默认使用 SANDBOX 环境。
     *
     * @param robotId 机器人 ID
     * @return 激活环境的 RobotEnvironment 实体，不存在返回 null
     */
    public RobotEnvironment getActiveEnvironment(String robotId) {
        Robot robot = robots.get(robotId);
        if (robot == null) return null;
        String envType = robot.getActiveEnv();
        if (envType == null || envType.isBlank()) envType = "SANDBOX";
        return environments.get(robotId + ":" + envType);
    }

    /**
     * 获取所有已注册的机器人
     *
     * @return 不可修改的 robotId -> Robot 映射视图
     */
    public Map<String, Robot> getAllRobots() {
        return Map.copyOf(robots);
    }

    /**
     * 检查机器人是否存在
     *
     * @param robotId 机器人 ID
     * @return true=已注册，false=不存在
     */
    public boolean hasRobot(String robotId) {
        return robots.containsKey(robotId);
    }

    /**
     * 设置机器人状态（1=正常，其他=停用）。
     *
     * <p>webhook 机器人停止后必须调用本方法（WebhookServiceImpl 读内存 Robot.status 拒收）。
     */
    public void setRobotStatus(String robotId, int status) {
        Robot r = robots.get(robotId);
        if (r != null) {
            r.setStatus(status);
            log.info("[机器人注册表] 设置机器人状态: id={}, status={}", robotId, status);
        }
    }

    /** 按 appId 注销机器人（含其全部环境配置）。 */
    public void unregisterRobot(String appId) {
        Robot r = findByAppId(appId);
        if (r != null) {
            robots.remove(r.getId());
            environments.entrySet().removeIf(e -> e.getKey().startsWith(r.getId() + ":"));
            log.info("[机器人注册表] 注销机器人: appId={}, robotId={}", appId, r.getId());
        }
    }
}
