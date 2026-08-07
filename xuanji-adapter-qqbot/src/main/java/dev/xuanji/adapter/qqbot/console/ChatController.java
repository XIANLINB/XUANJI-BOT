package dev.xuanji.adapter.qqbot.console;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qqbot.api.MessageSender;
import dev.xuanji.adapter.qqbot.storage.QqBotRepository;
import dev.xuanji.api.json.Json;
import dev.xuanji.core.web.XuanjiApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台 · 聊天窗口（对话调试台）：历史消息 / 主动发送（文本、Markdown）/ 富媒体上传发送。
 *
 * <p>机器人侧主动发送走 {@link MessageSender#runWithRobotContext} 显式绑定上下文，
 * 与事件处理线程无关；发送成功后自动落 OUT 消息记录（聊天窗口可见）。
 */
@Slf4j
@XuanjiApi
@RestController
@RequestMapping("/console/chat")
public class ChatController {

    private final MessageSender messageSender;
    private final QqBotRepository qqRepo;
    private final dev.xuanji.adapter.qqbot.registry.RobotRegistry robotRegistry;

    public ChatController(MessageSender messageSender, QqBotRepository qqRepo,
                          dev.xuanji.adapter.qqbot.registry.RobotRegistry robotRegistry) {
        this.messageSender = messageSender;
        this.qqRepo = qqRepo;
        this.robotRegistry = robotRegistry;
    }

    /** 某会话的历史消息：targetType = group | c2c，targetId = 群 openid 或用户 openid，倒序取最近 limit 条。 */
    @GetMapping("/messages")
    public Map<String, Object> messages(@RequestParam String bot,
                                        @RequestParam String targetType,
                                        @RequestParam String targetId,
                                        @RequestParam(defaultValue = "50") int limit) {
        List<Map<String, Object>> rows = qqRepo.listMessagesByTarget(bot, targetType, targetId,
                Math.min(Math.max(limit, 1), 200));
        // 倒序 → 正序（聊天窗口从上往下时间递增）
        java.util.Collections.reverse(rows);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bot", bot);
        m.put("targetType", targetType);
        m.put("targetId", targetId);
        m.put("rows", rows);
        return m;
    }

    /** 主动发送文本/Markdown 消息。msgType = text | markdown。 */
    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody Map<String, Object> body) {
        String bot = str(body.get("bot"));
        String targetType = str(body.get("targetType"));
        String targetId = str(body.get("targetId"));
        String msgType = str(body.get("msgType")).isBlank() ? "text" : str(body.get("msgType"));
        String content = str(body.get("content"));
        if (bot.isEmpty() || targetId.isEmpty() || content.isEmpty()) {
            return Map.of("status", "error", "msg", "bot/targetId/content 不能为空");
        }
        try {
            ObjectNode[] box = new ObjectNode[1];
            messageSender.runWithRobotContext(bot, () -> {
                boolean group = "group".equalsIgnoreCase(targetType);
                if ("markdown".equalsIgnoreCase(msgType)) {
                    ObjectNode md = Json.obj().put("content", content);
                    box[0] = group
                            ? messageSender.sendGroupMarkdown(targetId, md, null, null)
                            : messageSender.sendC2cMarkdown(targetId, md, null, null);
                } else {
                    box[0] = group
                            ? messageSender.sendGroupText(targetId, content, null)
                            : messageSender.sendC2cText(targetId, content, null);
                }
            });
            ObjectNode resp = box[0];
            log.info("[Chat] 发送成功: bot={} {} {} msgType={}", bot, targetType, targetId, msgType);
            return Map.of("status", "ok", "msgId",
                    resp != null ? resp.path("id").asText("") : "");
        } catch (Exception e) {
            log.error("[Chat] 发送失败: bot={} {} {} error={}", bot, targetType, targetId, e.getMessage());
            return Map.of("status", "error", "msg", e.getMessage());
        }
    }

    /** 富媒体发送：本地文件 base64 → multipart 上传 → 发送。fileType：1 图片 / 2 视频 / 3 语音。 */
    @PostMapping("/send-media")
    public Map<String, Object> sendMedia(@RequestBody Map<String, Object> body) {
        String bot = str(body.get("bot"));
        String targetType = str(body.get("targetType"));
        String targetId = str(body.get("targetId"));
        String base64 = str(body.get("base64"));
        String filename = str(body.get("filename")).isBlank() ? "file.bin" : str(body.get("filename"));
        int fileType = body.get("fileType") instanceof Number n ? n.intValue() : 1;
        if (bot.isEmpty() || targetId.isEmpty() || base64.isEmpty()) {
            return Map.of("status", "error", "msg", "bot/targetId/base64 不能为空");
        }
        try {
            String b64 = base64;
            int comma = b64.indexOf(',');
            if (comma >= 0) b64 = b64.substring(comma + 1); // 剥 data:image/png;base64, 前缀
            byte[] data = Base64.getDecoder().decode(b64);
            ObjectNode[] box = new ObjectNode[1];
            messageSender.runWithRobotContext(bot, () -> {
                boolean group = "group".equalsIgnoreCase(targetType);
                box[0] = group
                        ? messageSender.uploadAndSendGroupMediaFile(bot, envOf(bot), targetId, fileType, data, filename, null)
                        : messageSender.uploadAndSendC2cMediaFile(bot, envOf(bot), targetId, fileType, data, filename, null);
            });
            log.info("[Chat] 富媒体发送成功: bot={} {} {} type={} file={}", bot, targetType, targetId, fileType, filename);
            return Map.of("status", "ok", "msgId",
                    box[0] != null ? box[0].path("id").asText("") : "");
        } catch (Exception e) {
            log.error("[Chat] 富媒体发送失败: bot={} error={}", bot, e.getMessage());
            return Map.of("status", "error", "msg", e.getMessage());
        }
    }

    private String envOf(String bot) {
        try {
            var r = robotRegistry.getRobot(bot);
            return r != null && r.getActiveEnv() != null ? r.getActiveEnv() : "PRODUCTION";
        } catch (Exception e) {
            return "PRODUCTION";
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
