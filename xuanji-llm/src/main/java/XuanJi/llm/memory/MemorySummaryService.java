package XuanJi.llm.memory;

import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmMessage;
import XuanJi.llm.LlmService;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 记忆摘要分层 —— 每天凌晨用 LLM 把各维度的旧 DETAIL 记忆压缩为一段摘要，
 * 存入 SUMMARY 行（固定 key）。注入时摘要恒在 + 详情关键词命中，窗口有限但长期事实不丢。
 *
 * <p>被压缩的超过 60 天 DETAIL 记忆删除（信息已进摘要，避免表膨胀）；近期 DETAIL 保留供关键词检索。
 */
@Slf4j
@Component
public class MemorySummaryService {

    /** DETAIL 记忆保留天数（超过后压缩并删除） */
    private static final long DETAIL_RETENTION_SECONDS = 60L * 86400;

    private final MemoryService memoryService;
    private final LlmService llmService;
    private final LlmConfigStore configStore;

    public MemorySummaryService(MemoryService memoryService,
                                LlmService llmService,
                                LlmConfigStore configStore) {
        this.memoryService = memoryService;
        this.llmService = llmService;
        this.configStore = configStore;
    }

    /** 记忆压缩（P3 定时迁移：由 scheduler 系统任务 LLM_MEMORY_SUMMARY 触发，每天 03:40）。 */
    public void compressAll() {
        if (!configStore.get().isEnabled()) {
            log.debug("[MEMORY] 摘要压缩跳过：聊天总开关未开");
            return;
        }
        List<Map<String, String>> dims = memoryService.dimensions();
        int done = 0;
        for (Map<String, String> d : dims) {
            try {
                if (compress(d.get("botKey"), d.get("groupId"), d.get("userId"))) done++;
            } catch (Exception e) {
                log.warn("[MEMORY] 维度摘要失败 bot={} err={}", d.get("botKey"), e.getMessage());
            }
        }
        log.info("[MEMORY] 摘要压缩完成：{} 个维度", done);
    }

    /** 压缩单个维度：DETAIL → SUMMARY，并清理超期 DETAIL。 */
    public boolean compress(String botKey, String groupId, String userId) {
        List<Map<String, Object>> details = memoryService.listDetails(botKey, groupId, userId, 60);
        if (details.isEmpty()) return false;
        StringBuilder buf = new StringBuilder();
        for (Map<String, Object> m : details) {
            buf.append("- ").append(m.get("key")).append(": ").append(m.get("value")).append("\n");
        }
        String prompt = """
            你是长期记忆整理助手。把以下关于「这个群/这个人」的记忆条目，压缩成一段不超过 200 字的摘要，
            保留：人物关系、喜好、约定、重要事实。直接输出摘要，不要任何前缀或解释。
            当前旧摘要（如有，可合并）：%s

            记忆条目：
            %s
            """.formatted(
                memoryService.summary(botKey, groupId, userId) == null ? "(无)" : memoryService.summary(botKey, groupId, userId),
                buf);
        String summary = llmService.chat(
                List.of(LlmMessage.system("你是长期记忆整理助手。"), LlmMessage.user(prompt)),
                LlmChatOptions.defaults());
        if (summary == null || summary.isBlank()) return false;
        memoryService.saveSummary(botKey, groupId, userId, summary.trim());
        int deleted = memoryService.deleteOldDetails(botKey, groupId, userId, DETAIL_RETENTION_SECONDS);
        log.info("[MEMORY] 摘要完成 bot={} group={} user={} 压缩{}条 清理{}条", botKey, groupId, userId, details.size(), deleted);
        return true;
    }
}
