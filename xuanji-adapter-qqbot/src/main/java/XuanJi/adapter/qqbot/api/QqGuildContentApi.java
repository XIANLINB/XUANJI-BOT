package XuanJi.adapter.qqbot.api;

import tools.jackson.databind.node.ObjectNode;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import XuanJi.api.json.Json;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import XuanJi.adapter.qqbot.config.ConditionalOnQqbotEnabled;

/**
 * QQ 频道内容管理 API 客户端（文档「四、频道内容管理」17 个接口）。
 *
 * <p>公告 / 精华消息 / 日程 / 音频控制 / 上麦下麦 / 帖子（论坛）等频道内容域接口。
 * 与 {@link QqGuildApiService} 同样基于 {@link QqApiService}，请求体 {@link ObjectNode} 透传。
 */
@Slf4j
@Component
@ConditionalOnQqbotEnabled
public class QqGuildContentApi {

    private final QqApiService api;
    private final RobotRegistry robotRegistry;

    public QqGuildContentApi(QqApiService api, RobotRegistry robotRegistry) {
        this.api = api;
        this.robotRegistry = robotRegistry;
    }

    // ==================== 公告 ====================

    /** 创建频道公告：POST /guilds/{guildId}/announces（body 见文档：channel_id/message_id 等） */
    public ObjectNode createAnnounce(String robotId, String envType, String guildId, ObjectNode body) {
        return api.post(robotId, envType, "/guilds/" + guildId + "/announces", body);
    }

    /** 删除频道公告：DELETE /guilds/{guildId}/announces/{messageId} */
    public ObjectNode deleteAnnounce(String robotId, String envType, String guildId, String messageId) {
        return api.delete(robotId, envType,
                "/guilds/" + guildId + "/announces/" + messageId);
    }

    // ==================== 精华消息 ====================

    /** 加精华消息：PUT /channels/{channelId}/pins/{messageId} */
    public ObjectNode pinMessage(String robotId, String envType, String channelId, String messageId) {
        return api.put(robotId, envType,
                "/channels/" + channelId + "/pins/" + messageId, Json.obj());
    }

    /** 取消精华消息：DELETE /channels/{channelId}/pins/{messageId} */
    public ObjectNode unpinMessage(String robotId, String envType, String channelId, String messageId) {
        return api.delete(robotId, envType,
                "/channels/" + channelId + "/pins/" + messageId);
    }

    /** 精华消息列表：GET /channels/{channelId}/pins */
    public ObjectNode pinnedMessages(String robotId, String envType, String channelId) {
        return api.get(robotId, envType, "/channels/" + channelId + "/pins");
    }

    // ==================== 日程 ====================

    /** 日程列表：GET /channels/{channelId}/schedules */
    public ObjectNode schedules(String robotId, String envType, String channelId) {
        return api.get(robotId, envType, "/channels/" + channelId + "/schedules");
    }

    /** 创建日程：POST /channels/{channelId}/schedules（body 见文档：name/start_timestamp 等） */
    public ObjectNode createSchedule(String robotId, String envType, String channelId, ObjectNode body) {
        return api.post(robotId, envType, "/channels/" + channelId + "/schedules", body);
    }

    /** 日程详情：GET /channels/{channelId}/schedules/{scheduleId} */
    public ObjectNode schedule(String robotId, String envType, String channelId, String scheduleId) {
        return api.get(robotId, envType,
                "/channels/" + channelId + "/schedules/" + scheduleId);
    }

    /** 修改日程：PATCH /channels/{channelId}/schedules/{scheduleId} */
    public ObjectNode updateSchedule(String robotId, String envType, String channelId,
                                     String scheduleId, ObjectNode body) {
        return api.patch(robotId, envType,
                "/channels/" + channelId + "/schedules/" + scheduleId, body);
    }

    /** 删除日程：DELETE /channels/{channelId}/schedules/{scheduleId} */
    public ObjectNode deleteSchedule(String robotId, String envType, String channelId, String scheduleId) {
        return api.delete(robotId, envType,
                "/channels/" + channelId + "/schedules/" + scheduleId);
    }

    // ==================== 音频控制 / 上麦 ====================

    /** 音频控制：POST /channels/{channelId}/audio（body 见文档：audio_url/status 等） */
    public ObjectNode controlAudio(String robotId, String envType, String channelId, ObjectNode body) {
        return api.post(robotId, envType, "/channels/" + channelId + "/audio", body);
    }

    /** 机器人上麦：PUT /channels/{channelId}/mic */
    public ObjectNode enterMic(String robotId, String envType, String channelId) {
        return api.put(robotId, envType, "/channels/" + channelId + "/mic", Json.obj());
    }

    /** 机器人下麦：DELETE /channels/{channelId}/mic */
    public ObjectNode leaveMic(String robotId, String envType, String channelId) {
        return api.delete(robotId, envType, "/channels/" + channelId + "/mic");
    }

    // ==================== 帖子（论坛） ====================

    /** 帖子列表：GET /channels/{channelId}/threads */
    public ObjectNode threads(String robotId, String envType, String channelId) {
        return api.get(robotId, envType, "/channels/" + channelId + "/threads");
    }

    /** 发表帖子：PUT /channels/{channelId}/threads（body 见文档：title/content 等） */
    public ObjectNode createThread(String robotId, String envType, String channelId, ObjectNode body) {
        return api.put(robotId, envType, "/channels/" + channelId + "/threads", body);
    }

    /** 帖子详情：GET /channels/{channelId}/threads/{threadId} */
    public ObjectNode thread(String robotId, String envType, String channelId, String threadId) {
        return api.get(robotId, envType,
                "/channels/" + channelId + "/threads/" + threadId);
    }

    /** 删除帖子：DELETE /channels/{channelId}/threads/{threadId} */
    public ObjectNode deleteThread(String robotId, String envType, String channelId, String threadId) {
        return api.delete(robotId, envType,
                "/channels/" + channelId + "/threads/" + threadId);
    }

    // ==================== 默认机器人快捷方式 ====================

    /** 注册表第一个机器人的 robotId（无注册机器人时为空串）。 */
    public String currentRobotId() {
        return robotRegistry.getAllRobots().keySet().stream().findFirst().orElse("");
    }

    /** 默认环境（统一正式环境）。 */
    public String currentEnv() {
        return "PRODUCTION";
    }
}
