package com.qunxing.qq_bot_xuanji.core.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qunxing.qq_bot_xuanji.common.dto.SendMessageRequest;
import com.qunxing.qq_bot_xuanji.registry.RobotRegistry;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 消息发送服务
 *
 * <p>封装 QQ 开放平台的消息发送 API，提供简洁的发送接口。
 *
 * <h3>简洁 API（推荐）</h3>
 * <pre>{@code
 * messageSender.sendC2cText("user_openid", "你好！", "msg_id");
 * messageSender.sendGroupText("group_openid", "签到成功！", "msg_id");
 * }</pre>
 */
@Slf4j
@Component
public class MessageSender {

    private final QqApiService qqApiService;
    private final RobotRegistry robotRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前事件处理的机器人上下文（ThreadLocal） */
    private static final ThreadLocal<RobotContext> CURRENT_CONTEXT = new ThreadLocal<>();

    public MessageSender(QqApiService qqApiService, RobotRegistry robotRegistry) {
        this.qqApiService = qqApiService;
        this.robotRegistry = robotRegistry;
    }

    public static void setCurrentContext(Long robotId, String envType) {
        CURRENT_CONTEXT.set(new RobotContext(robotId, envType));
    }

    public static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    private Long getCurrentRobotId() {
        RobotContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) return ctx.robotId;
        return robotRegistry.getAllRobots().keySet().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("没有注册的机器人"));
    }

    private String getCurrentEnvType() {
        RobotContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) return ctx.envType;
        return "PRODUCTION";
    }

    // ==================== 单聊消息（简洁 API） ====================

    public JSONObject sendC2cText(String openid, String content, String msgId) {
        return sendC2cText(getCurrentRobotId(), getCurrentEnvType(), openid, content, msgId);
    }

    public JSONObject sendC2cText(String openid, String content) {
        return sendC2cText(getCurrentRobotId(), getCurrentEnvType(), openid, content, null);
    }

    public JSONObject sendC2cMarkdown(String openid, Object markdown, Object keyboard, String msgId) {
        return sendC2cMarkdown(getCurrentRobotId(), getCurrentEnvType(), openid, markdown, keyboard, msgId);
    }

    public JSONObject sendC2cMarkdown(String openid, Object markdown, Object keyboard) {
        return sendC2cMarkdown(getCurrentRobotId(), getCurrentEnvType(), openid, markdown, keyboard, null);
    }

    public JSONObject sendC2cArk(String openid, Object ark, String msgId) {
        return sendC2cArk(getCurrentRobotId(), getCurrentEnvType(), openid, ark, msgId);
    }

    public JSONObject sendC2cArk(String openid, Object ark) {
        return sendC2cArk(getCurrentRobotId(), getCurrentEnvType(), openid, ark, null);
    }

    public JSONObject sendC2cMessage(String openid, SendMessageRequest request) {
        return sendC2cMessage(getCurrentRobotId(), getCurrentEnvType(), openid, request);
    }

    // ==================== 群聊消息（简洁 API） ====================

    public JSONObject sendGroupText(String groupOpenid, String content, String msgId) {
        return sendGroupText(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, content, msgId);
    }

    public JSONObject sendGroupText(String groupOpenid, String content) {
        return sendGroupText(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, content, null);
    }

    public JSONObject sendGroupMarkdown(String groupOpenid, Object markdown, Object keyboard, String msgId) {
        return sendGroupMarkdown(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, markdown, keyboard, msgId);
    }

    public JSONObject sendGroupMarkdown(String groupOpenid, Object markdown, Object keyboard) {
        return sendGroupMarkdown(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, markdown, keyboard, null);
    }

    public JSONObject sendGroupArk(String groupOpenid, Object ark, String msgId) {
        return sendGroupArk(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, ark, msgId);
    }

    public JSONObject sendGroupArk(String groupOpenid, Object ark) {
        return sendGroupArk(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, ark, null);
    }

    public JSONObject sendGroupMessage(String groupOpenid, SendMessageRequest request) {
        return sendGroupMessage(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, request);
    }

    // ==================== 完整 API（多机器人场景） ====================

    public JSONObject sendC2cText(Long robotId, String envType, String openid, String content, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 0);
        body.put("content", content);
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] {}", openid, truncate(content, 50));
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendC2cMarkdown(Long robotId, String envType, String openid,
                                       Object markdown, Object keyboard, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 2);
        body.put("markdown", toJsonObject(markdown));
        if (keyboard != null) body.put("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [Markdown]", openid);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendC2cArk(Long robotId, String envType, String openid, Object ark, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 3);
        body.put("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            int templateId = toJsonObject(ark).optInt("template_id", 0);
            log.info("[发送单聊消息成功][用户{}] [Ark模板{}]", openid, templateId);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendC2cMedia(Long robotId, String envType, String openid, Object media, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 7);
        body.put("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [富媒体]", openid);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendC2cMessage(Long robotId, String envType, String openid, SendMessageRequest request) {
        try {
            JSONObject body = new JSONObject(objectMapper.writeValueAsString(request));
            JSONObject result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [msgType={}]", openid, request.getMsgType());
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw new RuntimeException("消息发送失败", e);
        }
    }

    public JSONObject sendGroupText(Long robotId, String envType, String groupOpenid, String content, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 0);
        body.put("content", content);
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] {}", groupOpenid, truncate(content, 50));
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendGroupMarkdown(Long robotId, String envType, String groupOpenid,
                                         Object markdown, Object keyboard, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 2);
        body.put("markdown", toJsonObject(markdown));
        if (keyboard != null) body.put("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [Markdown]", groupOpenid);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendGroupArk(Long robotId, String envType, String groupOpenid, Object ark, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 3);
        body.put("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            int templateId = toJsonObject(ark).optInt("template_id", 0);
            log.info("[发送群聊消息成功][群{}] [Ark模板{}]", groupOpenid, templateId);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendGroupMedia(Long robotId, String envType, String groupOpenid, Object media, String msgId) {
        JSONObject body = new JSONObject();
        body.put("msg_type", 7);
        body.put("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            JSONObject result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [富媒体]", groupOpenid);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public JSONObject sendGroupMessage(Long robotId, String envType, String groupOpenid, SendMessageRequest request) {
        try {
            JSONObject body = new JSONObject(objectMapper.writeValueAsString(request));
            JSONObject result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [msgType={}]", groupOpenid, request.getMsgType());
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw new RuntimeException("消息发送失败", e);
        }
    }

    // ==================== 媒体上传 + 发送 ====================

    /**
     * 上传群聊媒体文件并发送
     */
    public JSONObject uploadAndSendGroupMedia(Long robotId, String envType, String groupOpenid,
                                               int fileType, String fileUrl, String msgId) {
        JSONObject uploadBody = new JSONObject();
        uploadBody.put("file_type", fileType);
        uploadBody.put("url", fileUrl);
        uploadBody.put("srv_send_msg", false);

        try {
            // 上传文件（超时60秒）
            JSONObject uploadResult = qqApiService.postWithTimeout(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/files", uploadBody, 60);
            String fileInfo = uploadResult.optString("file_info");

            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传群聊媒体失败][群{}] 未获取到 file_info", groupOpenid);
                return null;
            }

            // 发送媒体消息
            JSONObject sendBody = new JSONObject();
            sendBody.put("msg_type", 7);
            sendBody.put("media", new JSONObject().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);

            JSONObject result = qqApiService.post(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/messages", sendBody);

            String typeName = switch (fileType) {
                case 1 -> "图片";
                case 2 -> "视频";
                case 3 -> "语音";
                default -> "媒体";
            };
            log.info("[发送群聊消息成功][群{}] [{}]", groupOpenid, typeName);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊媒体失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    /**
     * 上传单聊媒体文件并发送
     */
    public JSONObject uploadAndSendC2cMedia(Long robotId, String envType, String openid,
                                             int fileType, String fileUrl, String msgId) {
        JSONObject uploadBody = new JSONObject();
        uploadBody.put("file_type", fileType);
        uploadBody.put("url", fileUrl);
        uploadBody.put("srv_send_msg", false);

        try {
            JSONObject uploadResult = qqApiService.postWithTimeout(robotId, envType,
                    "/v2/users/" + openid + "/files", uploadBody, 60);
            String fileInfo = uploadResult.optString("file_info");

            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传单聊媒体失败][用户{}] 未获取到 file_info", openid);
                return null;
            }

            JSONObject sendBody = new JSONObject();
            sendBody.put("msg_type", 7);
            sendBody.put("media", new JSONObject().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);

            JSONObject result = qqApiService.post(robotId, envType,
                    "/v2/users/" + openid + "/messages", sendBody);

            String typeName = switch (fileType) {
                case 1 -> "图片";
                case 2 -> "视频";
                case 3 -> "语音";
                default -> "媒体";
            };
            log.info("[发送单聊消息成功][用户{}] [{}]", openid, typeName);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊媒体失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    // ==================== 简洁 API（自动使用当前上下文） ====================

    /**
     * 上传并发送群聊图片（简洁 API）
     */
    public JSONObject sendGroupImage(String groupOpenid, String imageUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 1, imageUrl, msgId);
    }

    /**
     * 上传并发送群聊语音（简洁 API）
     */
    public JSONObject sendGroupAudio(String groupOpenid, String audioUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 3, audioUrl, msgId);
    }

    /**
     * 上传并发送群聊视频（简洁 API）
     */
    public JSONObject sendGroupVideo(String groupOpenid, String videoUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 2, videoUrl, msgId);
    }

    /**
     * 上传并发送单聊图片（简洁 API）
     */
    public JSONObject sendC2cImage(String openid, String imageUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 1, imageUrl, msgId);
    }

    /**
     * 上传并发送单聊语音（简洁 API）
     */
    public JSONObject sendC2cAudio(String openid, String audioUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 3, audioUrl, msgId);
    }

    /**
     * 上传并发送单聊视频（简洁 API）
     */
    public JSONObject sendC2cVideo(String openid, String videoUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 2, videoUrl, msgId);
    }

    // ==================== 内部方法 ====================

    private JSONObject toJsonObject(Object obj) {
        if (obj instanceof JSONObject) return (JSONObject) obj;
        return new JSONObject(obj.toString());
    }

    private String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() > max ? str.substring(0, max) + "..." : str;
    }

    private record RobotContext(Long robotId, String envType) {}
}
