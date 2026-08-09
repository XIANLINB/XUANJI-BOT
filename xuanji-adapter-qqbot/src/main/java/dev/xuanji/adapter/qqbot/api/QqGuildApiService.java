package dev.xuanji.adapter.qqbot.api;

import tools.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qqbot.registry.RobotRegistry;
import dev.xuanji.api.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import dev.xuanji.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 频道管理 API 客户端（文档「三、频道管理」27 个接口）。
 *
 * <p>对 {@link QqApiService} 的进一步域封装：把频道/子频道/成员/身份组/权限/禁言等
 * 管理路径收敛为本类的强类型方法。请求体用 {@link ObjectNode} 透传（字段以官方文档为准，
 * 框架不臆造 DTO），返回原始 JSON 由调用方解析。
 *
 * <p>方法均需显式传 robotId + envType（频道接口是低频管理操作，不设隐式上下文）。
 * 未实现的快捷方式取默认机器人的 {@link #currentRobotId()} / {@link #currentEnv()}。
 */
@Slf4j
@Component
@ConditionalOnQqbotEnabled
public class QqGuildApiService {

    private final QqApiService api;
    private final RobotRegistry robotRegistry;

    public QqGuildApiService(QqApiService api, RobotRegistry robotRegistry) {
        this.api = api;
        this.robotRegistry = robotRegistry;
    }

    // ==================== 频道 ====================

    /** 获取频道详情：GET /guilds/{guildId} */
    public ObjectNode guild(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId);
    }

    /** 获取子频道列表：GET /guilds/{guildId}/channels */
    public ObjectNode channels(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/channels");
    }

    /** 创建子频道：POST /guilds/{guildId}/channels（body 见文档：name/type 等） */
    public ObjectNode createChannel(String robotId, String envType, String guildId, ObjectNode body) {
        return api.post(robotId, envType, "/guilds/" + guildId + "/channels", body);
    }

    /** 获取子频道详情：GET /channels/{channelId} */
    public ObjectNode channel(String robotId, String envType, String channelId) {
        return api.get(robotId, envType, "/channels/" + channelId);
    }

    /** 修改子频道：PATCH /channels/{channelId} */
    public ObjectNode updateChannel(String robotId, String envType, String channelId, ObjectNode body) {
        return api.patch(robotId, envType, "/channels/" + channelId, body);
    }

    /** 删除子频道：DELETE /channels/{channelId} */
    public ObjectNode deleteChannel(String robotId, String envType, String channelId) {
        return api.delete(robotId, envType, "/channels/" + channelId);
    }

    /** 子频道在线成员数：GET /channels/{channelId}/online_nums */
    public ObjectNode channelOnlineNums(String robotId, String envType, String channelId) {
        return api.get(robotId, envType, "/channels/" + channelId + "/online_nums");
    }

    // ==================== 成员 ====================

    /** 频道成员列表：GET /guilds/{guildId}/members */
    public ObjectNode members(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/members");
    }

    /** 频道成员详情：GET /guilds/{guildId}/members/{userId} */
    public ObjectNode member(String robotId, String envType, String guildId, String userId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/members/" + userId);
    }

    /** 移除频道成员：DELETE /guilds/{guildId}/members/{userId} */
    public ObjectNode removeMember(String robotId, String envType, String guildId, String userId) {
        return api.delete(robotId, envType, "/guilds/" + guildId + "/members/" + userId);
    }

    // ==================== 身份组（角色） ====================

    /** 身份组列表：GET /guilds/{guildId}/roles */
    public ObjectNode roles(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/roles");
    }

    /** 创建身份组：POST /guilds/{guildId}/roles */
    public ObjectNode createRole(String robotId, String envType, String guildId, ObjectNode body) {
        return api.post(robotId, envType, "/guilds/" + guildId + "/roles", body);
    }

    /** 修改身份组：PATCH /guilds/{guildId}/roles/{roleId} */
    public ObjectNode updateRole(String robotId, String envType, String guildId, String roleId, ObjectNode body) {
        return api.patch(robotId, envType, "/guilds/" + guildId + "/roles/" + roleId, body);
    }

    /** 删除身份组：DELETE /guilds/{guildId}/roles/{roleId} */
    public ObjectNode deleteRole(String robotId, String envType, String guildId, String roleId) {
        return api.delete(robotId, envType, "/guilds/" + guildId + "/roles/" + roleId);
    }

    /** 身份组成员列表：GET /guilds/{guildId}/roles/{roleId}/members */
    public ObjectNode roleMembers(String robotId, String envType, String guildId, String roleId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/roles/" + roleId + "/members");
    }

    /** 添加身份组成员：PUT /guilds/{guildId}/members/{userId}/roles/{roleId} */
    public ObjectNode addRoleMember(String robotId, String envType, String guildId, String userId, String roleId) {
        return api.put(robotId, envType,
                "/guilds/" + guildId + "/members/" + userId + "/roles/" + roleId, Json.obj());
    }

    /** 移除身份组成员：DELETE /guilds/{guildId}/members/{userId}/roles/{roleId} */
    public ObjectNode removeRoleMember(String robotId, String envType, String guildId, String userId, String roleId) {
        return api.delete(robotId, envType,
                "/guilds/" + guildId + "/members/" + userId + "/roles/" + roleId);
    }

    // ==================== 权限 ====================

    /** 用户子频道权限：GET /channels/{channelId}/members/{userId}/permissions */
    public ObjectNode memberPermissions(String robotId, String envType, String channelId, String userId) {
        return api.get(robotId, envType,
                "/channels/" + channelId + "/members/" + userId + "/permissions");
    }

    /** 修改用户子频道权限：PUT /channels/{channelId}/members/{userId}/permissions（body 见文档：add/remove 位图） */
    public ObjectNode updateMemberPermissions(String robotId, String envType, String channelId,
                                              String userId, ObjectNode body) {
        return api.put(robotId, envType,
                "/channels/" + channelId + "/members/" + userId + "/permissions", body);
    }

    /** 身份组子频道权限：GET /channels/{channelId}/roles/{roleId}/permissions */
    public ObjectNode rolePermissions(String robotId, String envType, String channelId, String roleId) {
        return api.get(robotId, envType,
                "/channels/" + channelId + "/roles/" + roleId + "/permissions");
    }

    /** 修改身份组子频道权限：PUT /channels/{channelId}/roles/{roleId}/permissions */
    public ObjectNode updateRolePermissions(String robotId, String envType, String channelId,
                                            String roleId, ObjectNode body) {
        return api.put(robotId, envType,
                "/channels/" + channelId + "/roles/" + roleId + "/permissions", body);
    }

    // ==================== API 权限申请 ====================

    /** 频道可用 API 权限：GET /guilds/{guildId}/api_permission */
    public ObjectNode apiPermissions(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/api_permission");
    }

    /** 申请 API 权限：POST /guilds/{guildId}/api_permission/demand */
    public ObjectNode demandApiPermission(String robotId, String envType, String guildId, ObjectNode body) {
        return api.post(robotId, envType, "/guilds/" + guildId + "/api_permission/demand", body);
    }

    // ==================== 消息频率 / 禁言 ====================

    /** 频道消息频率设置：GET /guilds/{guildId}/message/setting */
    public ObjectNode messageSetting(String robotId, String envType, String guildId) {
        return api.get(robotId, envType, "/guilds/" + guildId + "/message/setting");
    }

    /** 全员禁言：PATCH /guilds/{guildId}/mute（body 见文档：mute_end_timestamp / mute_seconds） */
    public ObjectNode muteAll(String robotId, String envType, String guildId, ObjectNode body) {
        return api.patch(robotId, envType, "/guilds/" + guildId + "/mute", body);
    }

    /** 指定成员禁言：PATCH /guilds/{guildId}/members/{userId}/mute */
    public ObjectNode muteMember(String robotId, String envType, String guildId, String userId, ObjectNode body) {
        return api.patch(robotId, envType,
                "/guilds/" + guildId + "/members/" + userId + "/mute", body);
    }

    // ==================== 默认机器人快捷方式 ====================

    /** 注册表第一个机器人的 robotId（无注册机器人时为空串）。 */
    public String currentRobotId() {
        return robotRegistry.getAllRobots().keySet().stream().findFirst().orElse("");
    }

    /** 默认环境（频道接口无沙箱语义，统一正式环境）。 */
    public String currentEnv() {
        return "PRODUCTION";
    }
}
