package XuanJi.llm.tool;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.summary.DailySummaryService;
import XuanJi.scheduler.store.SchedulerJobStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 更多 AI 工具 —— 结合框架能力补强：
 * 记忆查询/列表、定时任务列表/删除、日报生成。
 *
 * <p>这些工具让 AI 能"记得住自己记过什么"、"管理定时任务"、"主动汇报日报"，
 * 与 {@code memory_*}/{@code schedule_*} 用户诉求对齐。
 */
@Slf4j
@Service
public class MoreTools {

    private final MemoryService memoryService;
    private final SchedulerJobStore jobStore;
    private final DailySummaryService summaryService;

    public MoreTools(MemoryService memoryService, SchedulerJobStore jobStore,
                     DailySummaryService summaryService) {
        this.memoryService = memoryService;
        this.jobStore = jobStore;
        this.summaryService = summaryService;
    }

    @LlmTool(name = "memory_search",
            descriptionZh = "搜索机器人记忆",
            description = "检索长期记忆库。回答涉及具体事实（用户说过的话/喜好/约定/个人信息/之前发生的事）时必须优先调用本工具；记忆里查不到就明确告诉用户不确定，不要编造",
            confirm = false)
    public String memorySearch(
            @LlmToolParam(name = "keyword", value = "记忆关键词（可选，留空返回最近记忆）", required = false) String keyword,
            LlmToolContext ctx) {
        List<Map<String, Object>> all = memoryService.list(ctx.botKey(), ctx.groupId(), ctx.userId());
        List<Map<String, Object>> hit = all.stream()
                .filter(m -> keyword == null || keyword.isBlank()
                        || String.valueOf(m.get("key")).contains(keyword)
                        || String.valueOf(m.get("value")).contains(keyword))
                .limit(10)
                .toList();
        if (hit.isEmpty()) {
            return "没有找到相关记忆";
        }
        StringBuilder sb = new StringBuilder("长期记忆（" + hit.size() + " 条）：\n");
        for (Map<String, Object> m : hit) {
            sb.append("- ").append(m.get("key")).append(": ").append(m.get("value")).append("\n");
        }
        return sb.toString().trim();
    }

    @LlmTool(name = "memory_list",
            descriptionZh = "列出最近记忆",
            description = "列出当前会话已记住的全部长期记忆（用户问'你都记住了我什么'时调用）",
            confirm = false)
    public String memoryList(LlmToolContext ctx) {
        List<Map<String, Object>> all = memoryService.list(ctx.botKey(), ctx.groupId(), ctx.userId());
        if (all.isEmpty()) {
            return "当前还没有长期记忆";
        }
        StringBuilder sb = new StringBuilder("已记住的长期记忆（" + all.size() + " 条）：\n");
        for (Map<String, Object> m : all) {
            sb.append("- ").append(m.get("key")).append(": ").append(m.get("value")).append("\n");
        }
        return sb.toString().trim();
    }

    @LlmTool(name = "schedule_list",
            descriptionZh = "列出当前定时任务",
            description = "列出当前群的定时任务（用户问'有哪些定时任务/定时提醒'时调用）",
            confirm = false)
    public String scheduleList(LlmToolContext ctx) {
        List<Map<String, Object>> jobs = jobStore.listJobs().stream()
                .filter(j -> ctx.groupId() == null || ctx.groupId().isBlank()
                        || String.valueOf(j.get("target_id")).equals(ctx.groupId()))
                .limit(10)
                .toList();
        if (jobs.isEmpty()) {
            return "当前群没有定时任务";
        }
        StringBuilder sb = new StringBuilder("定时任务（" + jobs.size() + " 个）：\n");
        for (Map<String, Object> j : jobs) {
            sb.append("- #").append(j.get("id"))
              .append(" 「").append(j.get("name")).append("」")
              .append(" cron=").append(j.get("cron"));
            if (j.get("content") != null && !String.valueOf(j.get("content")).isBlank()) {
                sb.append(" 内容=").append(j.get("content"));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @LlmTool(name = "schedule_delete",
            descriptionZh = "删除一个定时任务",
            description = "删除一个定时任务（用户要求'取消/删除某个定时提醒/定时任务'时调用）。jobId 来自 schedule_list 的 #id",
            confirm = true)
    public String scheduleDelete(
            @LlmToolParam(name = "jobId", value = "要删除的任务 ID（schedule_list 里的 #id）") Long jobId) {
        if (jobId == null || jobId <= 0) {
            return "请提供有效的任务 ID";
        }
        boolean ok = jobStore.deleteJob(jobId);
        return ok ? "已删除定时任务 #" + jobId : "任务 #" + jobId + " 不存在或删除失败";
    }

    @LlmTool(name = "daily_summary",
            description = "立即生成当前群的今日日报总结并发送到群里（用户要求'生成日报/总结一下今天/今天群里聊了什么'时调用）",
            confirm = false)
    public String dailySummary(LlmToolContext ctx) {
        if (ctx.groupId() == null || ctx.groupId().isBlank()) {
            return "日报生成仅支持群聊场景";
        }
        boolean ok = summaryService.generateAndSend(ctx.botKey(), ctx.groupId(), false);
        return ok ? "今日日报已生成并发送到群里" : "日报生成失败（该群今日暂无消息，或 LLM 调用失败）";
    }
}