package XuanJi.llm.tool;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.scheduler.core.TaskSchedulerService;
import XuanJi.scheduler.store.SchedulerJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 定时任务类 LLM 工具 —— 自然语言建定时落地：
 * 用户说「每周五 15:00 提醒我喝水」，模型解析出 cron 与内容后调用
 * {@code create_schedule} 创建 BOT_PUSH 定时任务（推送回当前群）。
 *
 * <p>{@code confirm=true}：建任务有副作用，需用户确认后执行。
 */
@Slf4j
@Component
public class ScheduleTools {

    private final SchedulerJobStore jobStore;

    public ScheduleTools(SchedulerJobStore jobStore) {
        this.jobStore = jobStore;
    }

    @LlmTool(name = "create_schedule",
            descriptionZh = "创建定时任务（cron 驱动推送）",
            description = "创建定时任务。当用户表达定时/周期需求（如「每天8点提醒我」「每周五15点提醒喝水」）时调用。解析自然语言中的时间为 cron 表达式并创建任务",
            confirm = true)
    public String createSchedule(LlmToolContext ctx,
                                 @LlmToolParam(name = "cron", value = "cron 表达式（秒 分 时 日 月 周，6段），如 '0 0 15 * * 5' 表示每周五15点。LLM 负责把自然语言翻译成 cron") String cron,
                                 @LlmToolParam(name = "content", value = "任务要提醒/发送的内容，如 '该喝水了'") String content) {
        if (cron == null || cron.isBlank()) return "cron 不能为空";
        if (content == null || content.isBlank()) return "任务内容不能为空";
        if (!TaskSchedulerService.isValidCron(cron.trim())) {
            return "cron 表达式不合法: " + cron + "（格式：秒 分 时 日 月 周，如 '0 0 15 * * 5'）";
        }
        if (ctx == null || ctx.botKey() == null || ctx.botKey().isBlank()) {
            return "缺少机器人上下文，无法创建任务";
        }
        String groupId = ctx.groupId();
        if (groupId == null || groupId.isBlank()) {
            return "仅支持在群聊中创建定时推送任务";
        }
        try {
            Map<String, Object> job = new LinkedHashMap<>();
            job.put("name", "AI建任务-" + content.substring(0, Math.min(content.length(), 12)));
            job.put("jobType", "BOT_PUSH");
            job.put("cron", cron.trim());
            job.put("targetPlatform", "qqbot");
            job.put("targetBot", ctx.botKey());
            job.put("targetType", "GROUP");
            job.put("targetId", groupId);
            job.put("content", content.trim());
            job.put("enabled", true);
            long id = jobStore.createJob(job);
            log.info("[TOOL] 自然语言建定时成功: id={}, cron={}, content={}", id, cron.trim(), content);
            return "定时任务已创建（id=" + id + "）：cron=" + cron.trim() + "，内容=" + content.trim();
        } catch (Exception e) {
            log.warn("[TOOL] 建定时失败: {}", e.getMessage());
            return "创建失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
