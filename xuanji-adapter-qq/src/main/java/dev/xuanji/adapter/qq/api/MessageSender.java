package dev.xuanji.adapter.qq.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuanji.adapter.qq.dto.SendMessageRequest;
import dev.xuanji.adapter.qq.registry.RobotRegistry;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
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

    private static final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(0);

    private final QqApiService qqApiService;
    private final RobotRegistry robotRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前事件处理的机器人上下文（ThreadLocal） */
    private static final ThreadLocal<RobotContext> CURRENT_CONTEXT = new ThreadLocal<>();

    public MessageSender(QqApiService qqApiService, RobotRegistry robotRegistry) {
        this.qqApiService = qqApiService;
        this.robotRegistry = robotRegistry;
    }

    public static void setCurrentContext(String robotId, String envType) {
        CURRENT_CONTEXT.set(new RobotContext(robotId, envType));
    }

    public static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    private String getCurrentRobotId() {
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

    public ObjectNode sendC2cText(String openid, String content, String msgId) {
        return sendC2cText(getCurrentRobotId(), getCurrentEnvType(), openid, content, msgId);
    }

    public ObjectNode sendC2cText(String openid, String content) {
        return sendC2cText(getCurrentRobotId(), getCurrentEnvType(), openid, content, null);
    }

    public ObjectNode sendC2cMarkdown(String openid, Object markdown, Object keyboard, String msgId) {
        return sendC2cMarkdown(getCurrentRobotId(), getCurrentEnvType(), openid, markdown, keyboard, msgId);
    }

    public ObjectNode sendC2cMarkdown(String openid, Object markdown, Object keyboard) {
        return sendC2cMarkdown(getCurrentRobotId(), getCurrentEnvType(), openid, markdown, keyboard, null);
    }

    public ObjectNode sendC2cArk(String openid, Object ark, String msgId) {
        return sendC2cArk(getCurrentRobotId(), getCurrentEnvType(), openid, ark, msgId);
    }

    public ObjectNode sendC2cArk(String openid, Object ark) {
        return sendC2cArk(getCurrentRobotId(), getCurrentEnvType(), openid, ark, null);
    }

    public ObjectNode sendC2cMessage(String openid, SendMessageRequest request) {
        return sendC2cMessage(getCurrentRobotId(), getCurrentEnvType(), openid, request);
    }

    // ==================== 群聊消息（简洁 API） ====================

    public ObjectNode sendGroupText(String groupOpenid, String content, String msgId) {
        return sendGroupText(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, content, msgId);
    }

    public ObjectNode sendGroupText(String groupOpenid, String content) {
        return sendGroupText(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, content, null);
    }

    public ObjectNode sendGroupMarkdown(String groupOpenid, Object markdown, Object keyboard, String msgId) {
        return sendGroupMarkdown(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, markdown, keyboard, msgId);
    }

    public ObjectNode sendGroupMarkdown(String groupOpenid, Object markdown, Object keyboard) {
        return sendGroupMarkdown(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, markdown, keyboard, null);
    }

    public ObjectNode sendGroupArk(String groupOpenid, Object ark, String msgId) {
        return sendGroupArk(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, ark, msgId);
    }

    public ObjectNode sendGroupArk(String groupOpenid, Object ark) {
        return sendGroupArk(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, ark, null);
    }

    public ObjectNode sendGroupMessage(String groupOpenid, SendMessageRequest request) {
        return sendGroupMessage(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, request);
    }

    // ==================== 完整 API（多机器人场景） ====================

    public ObjectNode sendC2cText(String robotId, String envType, String openid, String content, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 0);
        body.put("content", content);
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] {}", openid, truncate(content, 50));
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cMarkdown(String robotId, String envType, String openid,
                                       Object markdown, Object keyboard, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 2);
        body.put("markdown", toJsonObject(markdown));
        if (keyboard != null) body.put("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [Markdown]", openid);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cArk(String robotId, String envType, String openid, Object ark, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 3);
        body.put("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            int templateId = toJsonObject(ark).path("template_id").asInt(0);
            log.info("[发送单聊消息成功][用户{}] [Ark模板{}]", openid, templateId);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cMedia(String robotId, String envType, String openid, Object media, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 7);
        body.put("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [富媒体]", openid);
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cMessage(String robotId, String envType, String openid, SendMessageRequest request) {
        try {
            ObjectNode body = Json.parseObj(objectMapper.writeValueAsString(request));
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [msgType={}]", openid, request.getMsgType());
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw new RuntimeException("消息发送失败", e);
        }
    }

    public ObjectNode sendGroupText(String robotId, String envType, String groupOpenid, String content, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 0);
        body.put("content", content);
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] {}", groupOpenid, truncate(content, 50));
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupMarkdown(String robotId, String envType, String groupOpenid,
                                         Object markdown, Object keyboard, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 2);
        body.put("markdown", toJsonObject(markdown));
        if (keyboard != null) body.put("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [Markdown]", groupOpenid);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupArk(String robotId, String envType, String groupOpenid, Object ark, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 3);
        body.put("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            int templateId = toJsonObject(ark).path("template_id").asInt(0);
            log.info("[发送群聊消息成功][群{}] [Ark模板{}]", groupOpenid, templateId);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupMedia(String robotId, String envType, String groupOpenid, Object media, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 7);
        body.put("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [富媒体]", groupOpenid);
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupMessage(String robotId, String envType, String groupOpenid, SendMessageRequest request) {
        try {
            ObjectNode body = Json.parseObj(objectMapper.writeValueAsString(request));
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
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
    public ObjectNode uploadAndSendGroupMedia(String robotId, String envType, String groupOpenid,
                                               int fileType, String fileUrl, String msgId) {
        ObjectNode uploadBody = Json.obj();
        uploadBody.put("file_type", fileType);
        uploadBody.put("url", fileUrl);
        uploadBody.put("srv_send_msg", false);

        try {
            // 上传文件（超时60秒）
            ObjectNode uploadResult = qqApiService.postWithTimeout(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/files", uploadBody, 60);
            String fileInfo = uploadResult.path("file_info").asText();

            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传群聊媒体失败][群{}] 未获取到 file_info", groupOpenid);
                return null;
            }

            // 发送媒体消息
            ObjectNode sendBody = Json.obj();
            sendBody.put("msg_type", 7);
            sendBody.put("media", Json.obj().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);

            ObjectNode result = qqApiService.post(robotId, envType,
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
    public ObjectNode uploadAndSendC2cMedia(String robotId, String envType, String openid,
                                             int fileType, String fileUrl, String msgId) {
        ObjectNode uploadBody = Json.obj();
        uploadBody.put("file_type", fileType);
        uploadBody.put("url", fileUrl);
        uploadBody.put("srv_send_msg", false);

        try {
            ObjectNode uploadResult = qqApiService.postWithTimeout(robotId, envType,
                    "/v2/users/" + openid + "/files", uploadBody, 60);
            String fileInfo = uploadResult.path("file_info").asText();

            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传单聊媒体失败][用户{}] 未获取到 file_info", openid);
                return null;
            }

            ObjectNode sendBody = Json.obj();
            sendBody.put("msg_type", 7);
            sendBody.put("media", Json.obj().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);

            ObjectNode result = qqApiService.post(robotId, envType,
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
    public ObjectNode sendGroupImage(String groupOpenid, String imageUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 1, imageUrl, msgId);
    }

    /**
     * 上传并发送群聊语音（简洁 API）
     */
    public ObjectNode sendGroupAudio(String groupOpenid, String audioUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 3, audioUrl, msgId);
    }

    /**
     * 上传并发送群聊视频（简洁 API）
     */
    public ObjectNode sendGroupVideo(String groupOpenid, String videoUrl, String msgId) {
        return uploadAndSendGroupMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, 2, videoUrl, msgId);
    }

    /**
     * 上传并发送单聊图片（简洁 API）
     */
    public ObjectNode sendC2cImage(String openid, String imageUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 1, imageUrl, msgId);
    }

    /**
     * 上传并发送单聊语音（简洁 API）
     */
    public ObjectNode sendC2cAudio(String openid, String audioUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 3, audioUrl, msgId);
    }

    /**
     * 上传并发送单聊视频（简洁 API）
     */
    public ObjectNode sendC2cVideo(String openid, String videoUrl, String msgId) {
        return uploadAndSendC2cMedia(getCurrentRobotId(), getCurrentEnvType(), openid, 2, videoUrl, msgId);
    }

    // ==================== 图文卡片 (msg_type=8) ====================

    public ObjectNode sendGroupCard(String groupOpenid, Object card, String msgId) {
        return sendGroupCard(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, card, msgId);
    }

    public ObjectNode sendGroupCard(String robotId, String envType, String groupOpenid, Object card, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 8);
        if (card instanceof ObjectNode) body.set("card", (ObjectNode) card);
        else body.put("card", card.toString());
        if (msgId != null && !msgId.isEmpty()) body.put("msg_id", msgId);
        return qqApiService.post(robotId, envType,
                "/v2/groups/" + groupOpenid + "/messages", body);
    }

    // ==================== 媒体上传（返回 file_info） ====================

    /** 上传媒体文件到 QQ 服务器，返回 file_info 字符串 */
    public String uploadMedia(String groupOpenid, int fileType, String fileUrl) {
        return uploadMedia(getCurrentRobotId(), getCurrentEnvType(), groupOpenid, fileType, fileUrl);
    }

    public String uploadMedia(String robotId, String envType, String groupOpenid, int fileType, String fileUrl) {
        ObjectNode body = Json.obj();
        body.put("file_type", fileType);
        body.put("url", fileUrl);
        ObjectNode result = qqApiService.post(robotId, envType,
                "/v2/groups/" + groupOpenid + "/files", body);
        if (result != null && result.has("file_info")) {
            return result.get("file_info").toString();
        }
        return null;
    }

    // ==================== 内部方法 ====================

    private ObjectNode toJsonObject(Object obj) {
        if (obj instanceof ObjectNode) return (ObjectNode) obj;
        return Json.parseObj(obj.toString());
    }

    private String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() > max ? str.substring(0, max) + "..." : str;
    }

    private void addMsgSeq(ObjectNode body) {
        body.put("msg_seq", seq.incrementAndGet());
    }

    // ==================== 消息撤回 ====================

    /** 撤回群聊消息 */
    public ObjectNode retractGroupMessage(String groupOpenid, String messageId) {
        String robotId = getCurrentRobotId();
        String envType = getCurrentEnvType();
        return qqApiService.delete(robotId, envType,
                "/v2/groups/" + groupOpenid + "/messages/" + messageId);
    }

    /** 撤回单聊消息 */
    public ObjectNode retractC2cMessage(String openid, String messageId) {
        String robotId = getCurrentRobotId();
        String envType = getCurrentEnvType();
        return qqApiService.delete(robotId, envType,
                "/v2/users/" + openid + "/messages/" + messageId);
    }

    // ==================== 群信息 ====================

    /** 获取群基础信息 */
    public ObjectNode getGroupInfo(String groupOpenid, String appId, String appSecret, String envType) {
        return qqApiService.get(appId, appSecret, envType,
                "/v2/groups/" + groupOpenid + "/info");
    }

    /** 获取机器人群内状态 */
    public ObjectNode getBotGroupState(String groupOpenid, String appId, String appSecret, String envType) {
        return qqApiService.get(appId, appSecret, envType,
                "/v2/groups/" + groupOpenid + "/bot_state");
    }

    private record RobotContext(String robotId, String envType) {}
}
