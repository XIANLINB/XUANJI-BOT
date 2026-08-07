package dev.xuanji.scheduler.exec;

import dev.xuanji.api.sender.SendReceipt;
import dev.xuanji.core.sender.BotPushSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * BOT_PUSH 执行器：定时向指定平台的群/用户推送文本消息。
 * 通过注入的 {@link BotPushSender} 列表按平台分发（无事件上下文也能发送）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotPushJobExecutor implements JobExecutor {

    private final List<BotPushSender> senders;

    @Override
    public String type() {
        return "BOT_PUSH";
    }

    @Override
    public String execute(Map<String, Object> job) throws Exception {
        String platform = str(job.get("targetPlatform"));
        String botKey = str(job.get("targetBot"));
        String targetType = str(job.get("targetType"));
        String targetId = str(job.get("targetId"));
        String content = str(job.get("content"));

        if (botKey.isEmpty() || targetId.isEmpty() || content.isEmpty()) {
            throw new IllegalArgumentException("BOT_PUSH 参数不完整（需要 botKey/targetId/content）");
        }
        BotPushSender sender = senders.stream()
                .filter(s -> s.platform().equalsIgnoreCase(platform))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无可用推送通道: " + platform));

        SendReceipt receipt = sender.push(botKey, targetType, targetId, content);
        if (!receipt.success()) {
            throw new IllegalStateException("推送失败: " + receipt.errorMessage());
        }
        return "推送成功 msgId=" + receipt.platformMsgId() + " (" + receipt.elapsedMs() + "ms)";
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
