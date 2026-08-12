package XuanJi.adapter.qqbot.api;

import tools.jackson.databind.ObjectMapper;
import XuanJi.adapter.qqbot.dto.SendMessageRequest;
import XuanJi.adapter.qqbot.model.Robot;
import XuanJi.adapter.qqbot.registry.RobotRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.node.ObjectNode;
import XuanJi.api.exception.BusinessException;
import XuanJi.api.json.Json;
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
    private final XuanJi.adapter.qqbot.storage.QqBotRepository qqRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 当前事件处理的机器人上下文（ThreadLocal） */
    private static final ThreadLocal<RobotContext> CURRENT_CONTEXT = new ThreadLocal<>();

    public MessageSender(QqApiService qqApiService, RobotRegistry robotRegistry,
                         XuanJi.adapter.qqbot.storage.QqBotRepository qqRepo) {
        this.qqApiService = qqApiService;
        this.robotRegistry = robotRegistry;
        this.qqRepo = qqRepo;
    }

    public static void setCurrentContext(String robotId, String envType) {
        CURRENT_CONTEXT.set(new RobotContext(robotId, envType));
    }

    public static void clearCurrentContext() {
        CURRENT_CONTEXT.remove();
    }

    /**
     * 在指定机器人的上下文中执行任务（出站队列线程专用）。
     *
     * <p>CURRENT_CONTEXT 是 ThreadLocal：事件处理线程设置的上下文到不了 BotOutboundExecutor
     * 的出站线程，导致 getCurrentRobotId() 回退到 getAllRobots().findFirst()——
     * 多机器人时取错 token（11255）。此方法在出站线程内显式重建上下文。
     */
    public void runWithRobotContext(String robotId, Runnable task) {
        String env = "PRODUCTION";
        try {
            Robot r = robotRegistry.getRobot(robotId);
            if (r != null && r.getActiveEnv() != null) env = r.getActiveEnv();
        } catch (Exception ignored) { /* 环境解析失败用默认 */ }
        setCurrentContext(robotId, env);
        try {
            task.run();
        } finally {
            clearCurrentContext();
        }
    }

    private String getCurrentRobotId() {
        RobotContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) return ctx.robotId;
        // 命令/事件处理线程：优先用「当前正在处理的机器人」（CommandRegistry 事件线程绑定的 botKey=appId），
        // 避免多机器人时命令回复/插件发送回退到第一个注册机器人（11255 同源问题）。
        String cmdBot = XuanJi.core.command.CommandRegistry.getCurrentBotKey();
        if (cmdBot != null && !cmdBot.isBlank()) return cmdBot;
        return robotRegistry.getAllRobots().keySet().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("没有注册的机器人"));
    }

    private String getCurrentEnvType() {
        RobotContext ctx = CURRENT_CONTEXT.get();
        if (ctx != null) return ctx.envType;
        return "PRODUCTION";
    }

    /** 当前上下文机器人 ID（无上下文回退第一个机器人），供统一发送出口 {@code QqXuanJiMessageSender} 使用。 */
    public String currentRobotId() {
        return getCurrentRobotId();
    }

    /** 当前上下文环境（无上下文默认 PRODUCTION），供统一发送出口使用。 */
    public String currentEnvType() {
        return getCurrentEnvType();
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
            recordOutbound(robotId, "c2c", openid, "text", content, newMsgId(result, msgId), body.toString());
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
        body.set("markdown", toJsonObject(markdown));
        if (keyboard != null) body.set("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [Markdown]", openid);
            recordOutbound(robotId, "c2c", openid, "markdown", String.valueOf(toJsonObject(markdown).path("content").asText("")), newMsgId(result, msgId), body.toString());
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cArk(String robotId, String envType, String openid, Object ark, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 3);
        body.set("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            int templateId = toJsonObject(ark).path("template_id").asInt(0);
            log.info("[发送单聊消息成功][用户{}] [Ark模板{}]", openid, templateId);
            recordOutbound(robotId, "c2c", openid, "ark", "t=" + templateId, newMsgId(result, msgId), body.toString());
            return result;
        } catch (Exception e) {
            log.error("[发送单聊消息失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendC2cMedia(String robotId, String envType, String openid, Object media, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 7);
        body.set("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/users/" + openid + "/messages", body);
            log.info("[发送单聊消息成功][用户{}] [富媒体]", openid);
            recordOutbound(robotId, "c2c", openid, "rich_media", toJsonObject(media).toString(), newMsgId(result, msgId), body.toString());
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
            recordOutbound(robotId, "c2c", openid,
                    XuanJi.adapter.qqbot.storage.QqBotRepository.msgTypeLabel(request.getMsgType()),
                    body.path("content").asText(""), newMsgId(result, null), body.toString());
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
            recordOutbound(robotId, "group", groupOpenid, "text", content, newMsgId(result, msgId), body.toString());
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
        body.set("markdown", toJsonObject(markdown));
        if (keyboard != null) body.set("keyboard", toJsonObject(keyboard));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [Markdown]", groupOpenid);
            recordOutbound(robotId, "group", groupOpenid, "markdown",
                    String.valueOf(toJsonObject(markdown).path("content").asText("")), newMsgId(result, msgId), body.toString());
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupArk(String robotId, String envType, String groupOpenid, Object ark, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 3);
        body.set("ark", toJsonObject(ark));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            int templateId = toJsonObject(ark).path("template_id").asInt(0);
            log.info("[发送群聊消息成功][群{}] [Ark模板{}]", groupOpenid, templateId);
            recordOutbound(robotId, "group", groupOpenid, "ark", "t=" + templateId, newMsgId(result, msgId), body.toString());
            return result;
        } catch (Exception e) {
            log.error("[发送群聊消息失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    public ObjectNode sendGroupMedia(String robotId, String envType, String groupOpenid, Object media, String msgId) {
        ObjectNode body = Json.obj();
        body.put("msg_type", 7);
        body.set("media", toJsonObject(media));
        if (msgId != null) body.put("msg_id", msgId);

        try {
            addMsgSeq(body);
            ObjectNode result = qqApiService.post(robotId, envType, "/v2/groups/" + groupOpenid + "/messages", body);
            log.info("[发送群聊消息成功][群{}] [富媒体]", groupOpenid);
            recordOutbound(robotId, "group", groupOpenid, "rich_media", toJsonObject(media).toString(), newMsgId(result, msgId), body.toString());
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
            recordOutbound(robotId, "group", groupOpenid,
                    XuanJi.adapter.qqbot.storage.QqBotRepository.msgTypeLabel(request.getMsgType()),
                    body.path("content").asText(""), newMsgId(result, null), body.toString());
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
            sendBody.set("media", Json.obj().put("file_info", fileInfo));
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
            // 出站事件计数（运行监控 QPS 出站曲线数据源）
            XuanJi.core.metric.QpsMeter.hitOut();
            // OUT 落库（per-bot qqbot_message，富媒体回复在消息监控显示「发送」方向）
            try {
                String msgType = switch (fileType) {
                    case 1 -> "image";
                    case 2 -> "video";
                    case 3 -> "voice";
                    default -> "file";
                };
                qqRepo.insertMessage(robotId, "group", groupOpenid, robotId,
                        "OUT", msgType, fileUrl, msgId, null, null, null,
                        XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OUT落库] 失败: {}", ex.getMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("[发送群聊媒体失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    /**
     * 上传语音（base64 file_data，无需公网 URL）并发送语音消息。
     *
     * <p>QQ 开放平台富媒体上传支持 {@code file_data}（base64 二进制数据）字段，
     * 免去公网 URL 依赖；语音格式支持 wav/mp3/flac/silk（file_type=3）。
     */
    public ObjectNode uploadAndSendGroupVoice(String robotId, String envType, String groupOpenid,
                                              byte[] audioData, String msgId) {
        ObjectNode uploadBody = Json.obj();
        uploadBody.put("file_type", 3);
        uploadBody.put("file_data", java.util.Base64.getEncoder().encodeToString(audioData));
        uploadBody.put("srv_send_msg", false);
        try {
            ObjectNode uploadResult = qqApiService.post(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/files", uploadBody);
            String fileInfo = uploadResult.path("file_info").asText();
            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传群聊语音失败][群{}] 未获取到 file_info", groupOpenid);
                return null;
            }
            ObjectNode sendBody = Json.obj();
            sendBody.put("msg_type", 7);
            sendBody.set("media", Json.obj().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);
            ObjectNode result = qqApiService.post(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/messages", sendBody);
            log.info("[发送群聊语音成功][群{}] {}B", groupOpenid, audioData.length);
            XuanJi.core.metric.QpsMeter.hitOut();
            try {
                qqRepo.insertMessage(robotId, "group", groupOpenid, robotId,
                        "OUT", "voice", "tts-" + audioData.length + "B", msgId, null, null, null,
                        XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OUT落库] 失败: {}", ex.getMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("[发送群聊语音失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    /**
     * 上传群聊媒体（base64 file_data 方式，无需公网 URL）并发送。
     *
     * <p>QQ 富媒体上传对图片/语音均支持 {@code file_data}（base64 二进制）字段：
     * file_type=1 图片 / 2 视频 / 3 语音。本地渲染的卡片/表情包走此通道。
     */
    public ObjectNode uploadAndSendGroupMediaData(String robotId, String envType, String groupOpenid,
                                                  int fileType, byte[] fileData, String msgId) {
        ObjectNode uploadBody = Json.obj();
        uploadBody.put("file_type", fileType);
        uploadBody.put("file_data", java.util.Base64.getEncoder().encodeToString(fileData));
        uploadBody.put("srv_send_msg", false);
        try {
            ObjectNode uploadResult = qqApiService.post(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/files", uploadBody);
            String fileInfo = uploadResult.path("file_info").asText();
            if (fileInfo == null || fileInfo.isEmpty()) {
                log.error("[上传群聊媒体失败][群{}] 未获取到 file_info", groupOpenid);
                return null;
            }
            ObjectNode sendBody = Json.obj();
            sendBody.put("msg_type", 7);
            sendBody.set("media", Json.obj().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);
            ObjectNode result = qqApiService.post(robotId, envType,
                    "/v2/groups/" + groupOpenid + "/messages", sendBody);
            log.info("[发送群聊媒体成功][群{}] fileType={} {}B", groupOpenid, fileType, fileData.length);
            XuanJi.core.metric.QpsMeter.hitOut();
            try {
                String typeName = switch (fileType) {
                    case 1 -> "image";
                    case 2 -> "video";
                    case 3 -> "voice";
                    default -> "media";
                };
                qqRepo.insertMessage(robotId, "group", groupOpenid, robotId,
                        "OUT", typeName, fileType + "-" + fileData.length + "B", msgId, null, null, null,
                        XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OUT落库] 失败: {}", ex.getMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("[发送群聊媒体失败][群{}] {}", groupOpenid, e.getMessage());
            throw e;
        }
    }

    /**
     * 上传单聊媒体（base64 file_data 方式，无需公网 URL）并发送。
     *
     * <p>与 {@link #uploadAndSendGroupMediaData} 对应，目标为用户 openid：
     * POST /v2/users/{openid}/files + file_data base64 → file_info → msg_type=7 发送。
     */
    public ObjectNode uploadAndSendC2cMediaData(String robotId, String envType, String openid,
                                                int fileType, byte[] fileData, String msgId) {
        ObjectNode uploadBody = Json.obj();
        uploadBody.put("file_type", fileType);
        uploadBody.put("file_data", java.util.Base64.getEncoder().encodeToString(fileData));
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
            sendBody.set("media", Json.obj().put("file_info", fileInfo));
            if (msgId != null) sendBody.put("msg_id", msgId);
            ObjectNode result = qqApiService.post(robotId, envType,
                    "/v2/users/" + openid + "/messages", sendBody);
            log.info("[发送单聊媒体成功][用户{}] fileType={} {}B", openid, fileType, fileData.length);
            XuanJi.core.metric.QpsMeter.hitOut();
            try {
                String typeName = switch (fileType) {
                    case 1 -> "image";
                    case 2 -> "video";
                    case 3 -> "voice";
                    default -> "media";
                };
                qqRepo.insertMessage(robotId, "c2c", openid, robotId,
                        "OUT", typeName, fileType + "-" + fileData.length + "B", msgId, null, null, null,
                        XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OUT落库] 失败: {}", ex.getMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("[发送单聊媒体失败][用户{}] {}", openid, e.getMessage());
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
            sendBody.set("media", Json.obj().put("file_info", fileInfo));
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
            // 出站事件计数（运行监控 QPS 出站曲线数据源）
            XuanJi.core.metric.QpsMeter.hitOut();
            // OUT 落库（per-bot qqbot_message，富媒体回复在消息监控显示「发送」方向）
            try {
                String msgType = switch (fileType) {
                    case 1 -> "image";
                    case 2 -> "video";
                    case 3 -> "voice";
                    default -> "file";
                };
                qqRepo.insertMessage(robotId, "c2c", null, openid,
                        "OUT", msgType, fileUrl, msgId, null, null, null,
                        XuanJi.core.util.TimeUtils.nowEpochSeconds());
            } catch (Exception ex) {
                log.debug("[OUT落库] 失败: {}", ex.getMessage());
            }
            return result;
        } catch (Exception e) {
            log.error("[发送单聊媒体失败][用户{}] {}", openid, e.getMessage());
            throw e;
        }
    }

    // ==================== 本地文件富媒体上传发送（multipart 流方式，聊天窗口用） ====================

    /**
     * 上传并发送群聊媒体（本地文件流 multipart 方式，供聊天窗口上传本地文件）。
     */
    public ObjectNode uploadAndSendGroupMediaFile(String robotId, String envType, String groupOpenid,
                                                  int fileType, byte[] fileBytes, String filename, String msgId) {
        ObjectNode uploadResult = qqApiService.postMultipart(robotId, envType,
                "/v2/groups/" + groupOpenid + "/files", fileType, fileBytes, filename, 60);
        return sendMediaByFileInfo(robotId, envType, "group", groupOpenid, groupOpenid,
                fileType, uploadResult.path("file_info").asText(), filename, msgId);
    }

    /**
     * 上传并发送单聊媒体（本地文件流 multipart 方式，供聊天窗口上传本地文件）。
     */
    public ObjectNode uploadAndSendC2cMediaFile(String robotId, String envType, String openid,
                                                int fileType, byte[] fileBytes, String filename, String msgId) {
        ObjectNode uploadResult = qqApiService.postMultipart(robotId, envType,
                "/v2/users/" + openid + "/files", fileType, fileBytes, filename, 60);
        return sendMediaByFileInfo(robotId, envType, "c2c", openid, openid,
                fileType, uploadResult.path("file_info").asText(), filename, msgId);
    }

    /** 已拿到 file_info 后统一发送媒体消息 + OUT 落库。 */
    private ObjectNode sendMediaByFileInfo(String robotId, String envType, String chatType,
                                           String groupOpenid, String userOpenid,
                                           int fileType, String fileInfo, String fileUrl, String msgId) {
        if (fileInfo == null || fileInfo.isEmpty()) {
            throw new IllegalStateException("富媒体上传未返回 file_info");
        }
        ObjectNode sendBody = Json.obj();
        sendBody.put("msg_type", 7);
        sendBody.set("media", Json.obj().put("file_info", fileInfo));
        if (msgId != null) sendBody.put("msg_id", msgId);

        String path = "group".equals(chatType)
                ? "/v2/groups/" + groupOpenid + "/messages"
                : "/v2/users/" + userOpenid + "/messages";
        ObjectNode result = qqApiService.post(robotId, envType, path, sendBody);

        String typeName = switch (fileType) {
            case 1 -> "图片";
            case 2 -> "视频";
            case 3 -> "语音";
            default -> "媒体";
        };
        log.info("[发送{}消息成功][{}] [{}]", "group".equals(chatType) ? "群聊" : "单聊",
                "group".equals(chatType) ? groupOpenid : userOpenid, typeName);
        // 出站事件计数（运行监控 QPS 出站曲线数据源）
        XuanJi.core.metric.QpsMeter.hitOut();
        // OUT 落库（per-bot qqbot_message，聊天窗口显示「发送」方向）
        try {
            String msgType = switch (fileType) {
                case 1 -> "image";
                case 2 -> "video";
                case 3 -> "voice";
                default -> "file";
            };
            qqRepo.insertMessage(robotId, chatType,
                    "group".equals(chatType) ? groupOpenid : null,
                    "group".equals(chatType) ? groupOpenid : userOpenid,
                    "OUT", msgType, fileUrl, msgId, null, null, null,
                    XuanJi.core.util.TimeUtils.nowEpochSeconds());
        } catch (Exception ex) {
            log.debug("[OUT落库] 失败: {}", ex.getMessage());
        }
        return result;
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
        ObjectNode result = qqApiService.post(robotId, envType,
                "/v2/groups/" + groupOpenid + "/messages", body);
        recordOutbound(robotId, "group", groupOpenid, "card", card != null ? card.toString() : "", newMsgId(result, msgId), body.toString());
        return result;
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

    /**
     * OUT 方向统一落库（per-bot qqbot_message，消息监控「发送」方向）。
     * 所有发送方法（文本/Markdown/Ark/富媒体/卡片）在真正发送成功后调用，
     * raw 记录出站请求体，保证「发出的消息有原始数据、类型正确」。
     * userId 以机器人自身 selfId 标识发送者。
     */
    /** OUT 落库用本次发送返回的新消息 ID（result.id）；无 id（如 204）回退传入 msgId（replyTo）。 */
    private static String newMsgId(ObjectNode result, String fallback) {
        if (result != null) {
            String id = result.path("id").asText(null);
            if (id != null && !id.isBlank()) return id;
        }
        return fallback;
    }

    private void recordOutbound(String robotId, String chatType, String targetId,
                                String msgType, String content, String msgId, String rawJson) {
        // 出站事件计数（运行监控 QPS 出站曲线数据源）：所有发送成功路径都会经过这里
        XuanJi.core.metric.QpsMeter.hitOut();
        try {
            qqRepo.insertMessage(robotId, chatType, targetId, robotId,
                    "OUT", msgType, content, msgId, null, null, rawJson,
                    XuanJi.core.util.TimeUtils.nowEpochSeconds());
        } catch (Exception ex) {
            log.debug("[OUT落库] 失败: {}", ex.getMessage());
        }
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

    /** 获取群基础信息（旧签名，appId/appSecret 直调） */
    public ObjectNode getGroupInfo(String groupOpenid, String appId, String appSecret, String envType) {
        return qqApiService.get(appId, appSecret, envType,
                "/v2/groups/" + groupOpenid + "/info");
    }

    /** 获取机器人群内状态（旧签名，appId/appSecret 直调） */
    public ObjectNode getBotGroupState(String groupOpenid, String appId, String appSecret, String envType) {
        return qqApiService.get(appId, appSecret, envType,
                "/v2/groups/" + groupOpenid + "/bot_state");
    }

    // ==================== 群管理（v2 新能力，robotId/envType 上下文签名） ====================

    /** 获取群基础信息（当前机器人上下文）。 */
    public ObjectNode getGroupInfo(String groupOpenid) {
        return qqApiService.get(currentRobotId(), currentEnvType(),
                "/v2/groups/" + groupOpenid + "/info");
    }

    /** 获取群基础信息（全参）。 */
    public ObjectNode getGroupInfo(String robotId, String envType, String groupOpenid) {
        return qqApiService.get(robotId, envType, "/v2/groups/" + groupOpenid + "/info");
    }

    /** 机器人在群内的状态（当前机器人上下文）。 */
    public ObjectNode getBotGroupState(String groupOpenid) {
        return qqApiService.get(currentRobotId(), currentEnvType(),
                "/v2/groups/" + groupOpenid + "/bot_state");
    }

    /** 机器人在群内的状态（全参）。 */
    public ObjectNode getBotGroupState(String robotId, String envType, String groupOpenid) {
        return qqApiService.get(robotId, envType, "/v2/groups/" + groupOpenid + "/bot_state");
    }

    /** 入群申请列表（当前机器人上下文）。 */
    public ObjectNode listGroupJoinRequests(String groupOpenid) {
        return listGroupJoinRequests(currentRobotId(), currentEnvType(), groupOpenid, null, null);
    }

    /**
     * 入群申请列表（全参，支持分页）。
     *
     * @param cursor  分页游标（首次可不传或传空串，可选）
     * @param pageLimit 每页条数（可选，默认 20，最大 100）
     */
    public ObjectNode listGroupJoinRequests(String robotId, String envType, String groupOpenid,
                                            String cursor, Integer pageLimit) {
        StringBuilder path = new StringBuilder("/v2/groups/").append(groupOpenid).append("/join_request_list");
        boolean first = true;
        if (cursor != null && !cursor.isBlank()) {
            path.append('?').append("cursor=").append(java.net.URLEncoder.encode(cursor, java.nio.charset.StandardCharsets.UTF_8));
            first = false;
        }
        if (pageLimit != null) {
            path.append(first ? '?' : '&').append("limit=").append(pageLimit);
        }
        return qqApiService.get(robotId, envType, path.toString());
    }

    /**
     * 入群申请审批（当前机器人上下文）。
     *
     * @param memberOpenid  申请者 openid
     * @param approved      true=同意入群 false=拒绝
     * @param rejectReason  拒绝理由（拒绝时可选）
     */
    /**
     * 入群申请审批（官方 POST /v2/groups/{group_openid}/approval_join_request/{member_openid}）。
     *
     * @param joinRequestId 申请 ID（必填，审批令牌定位用；来自入群申请事件/列表的 join_request_id）
     * @param approved      true=通过(approve)，false=拒绝(decline)
     * @param rejectReason  拒绝理由（op=decline 时可选）
     * @param addBlacklist  是否同时加入群黑名单（op=decline 时可选）
     */
    public ObjectNode approveGroupJoinRequest(String groupOpenid, String memberOpenid, String joinRequestId,
                                              boolean approved, String rejectReason, Boolean addBlacklist) {
        // join_request_id 必传（平台文档标注可选，但实测缺省报 11004「无效或已过期的审批令牌」）
        if (joinRequestId == null || joinRequestId.isBlank()) {
            throw new XuanJi.api.exception.BusinessException(400, "入群审批失败：缺少必传参数 join_request_id");
        }
        ObjectNode body = Json.obj();
        body.put("op", approved ? "approve" : "decline");
        body.put("join_request_id", joinRequestId);
        if (rejectReason != null && !rejectReason.isBlank()) {
            body.put("reject_reason", rejectReason);
        }
        if (addBlacklist != null && Boolean.TRUE.equals(addBlacklist)) {
            body.put("add_to_member_blacklist", true);
        }
        return qqApiService.post(currentRobotId(), currentEnvType(),
                "/v2/groups/" + groupOpenid + "/approval_join_request/" + memberOpenid, body);
    }

    /** 查询群禁言状态（当前机器人上下文）。 */
    public ObjectNode getGroupRestrictSetting(String groupOpenid) {
        return getGroupRestrictSetting(currentRobotId(), currentEnvType(), groupOpenid);
    }

    /** 查询群禁言状态（全参）。 */
    public ObjectNode getGroupRestrictSetting(String robotId, String envType, String groupOpenid) {
        return qqApiService.get(robotId, envType,
                "/v2/groups/" + groupOpenid + "/restrict_chat_setting");
    }

    /**
     * 设置群成员禁言（POST /v2/groups/{openid}/restrict_chat_setting，60 QPM）。
     *
     * <p>官方请求体为 {@code members[]}（单次最多 10 个），每项：{@code op}
     * （add 增加禁言 / update 更新到期时间 / del 解除禁言）、{@code member_openid}
     * （被禁言成员，仅能操作普通成员，不能是群主/管理员/机器人）、{@code mute_expire_at}
     * （禁言到期时间 RFC3339 格式；op=del 时传空串表示立即解除）。
     *
     * @param memberOpenid 被禁言成员 openid
     * @param op           add / update / del
     * @param muteExpireAt 禁言到期时间（RFC3339）；del 时传空串立即解除
     */
    public ObjectNode setGroupMute(String robotId, String envType, String groupOpenid,
                                   String memberOpenid, String op, String muteExpireAt) {
        ObjectNode member = Json.obj();
        member.put("op", op);
        member.put("member_openid", memberOpenid);
        member.put("mute_expire_at", muteExpireAt == null ? "" : muteExpireAt);
        ObjectNode body = Json.obj();
        body.putArray("members").add(member);
        return qqApiService.post(robotId, envType,
                "/v2/groups/" + groupOpenid + "/restrict_chat_setting", body);
    }

    /**
     * 生成机器人分享链接（POST /v2/generate_url_link，50 QPS）。
     *
     * <p>用户通过分享链接添加机器人时 {@code callbackData} 会透传给开发者（最长 32 字符）。
     *
     * @param callbackData 自定义透传参数（可选，≤32 字符）
     * @return 分享链接；平台失败返回 null
     */
    public String generateShareLink(String callbackData) {
        return qqApiService.generateShareLink(currentRobotId(), currentEnvType(), null, callbackData);
    }

    private record RobotContext(String robotId, String envType) {}
}
