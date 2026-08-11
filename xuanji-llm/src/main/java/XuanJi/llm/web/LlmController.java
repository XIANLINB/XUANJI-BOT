package XuanJi.llm.web;

import XuanJi.api.llm.LlmMessage;
import XuanJi.core.web.XuanJiApi;
import XuanJi.llm.LlmService;
import XuanJi.llm.chat.LlmChatGuard;
import XuanJi.llm.chat.LlmQuotaService;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.mcp.McpService;
import XuanJi.llm.memory.MemoryService;
import XuanJi.llm.persona.LlmPersona;
import XuanJi.llm.persona.PersonaService;
import XuanJi.llm.persona.PersonaTemplates;
import XuanJi.llm.proactive.ProactiveChatService;
import XuanJi.llm.profile.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 控制台 AI 能力 API —— LLM 模块自治入口。
 *
 * <p>标注 {@link XuanJiApi}，实际暴露路径为 {@code /xuanji/api/v1/console/llm/**}
 * （前缀由 console-server 的 XuanJiApiRoutes 统一注入）。返回风格与现有控制台
 * controller 一致（直接返回数据对象，不额外包 R）。
 *
 * <p>P0：供应商/配置/测试；P1：流式对话(SSE)+人格；P1.5：用量统计/群限额/记忆。
 */
@XuanJiApi
@RestController
@RequestMapping("/console/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmService llmService;
    private final PersonaService personaService;
    private final PersonaTemplates personaTemplates;
    private final LlmQuotaService quotaService;
    private final LlmChatGuard guard;
    private final MemoryService memoryService;
    private final UserProfileService profileService;
    private final ProactiveChatService proactiveService;
    private final XuanJi.llm.tool.ToolRegistry toolRegistry;
    private final McpService mcpService;
    private final XuanJi.llm.kb.KbService kbService;
    private final XuanJi.llm.audit.LlmAuditService auditService;
    private final XuanJi.llm.summary.DailySummaryService summaryService;
    private final XuanJi.llm.provider.ProviderService providerService;
    private final XuanJi.llm.render.HtmlRenderService renderService;
    private final XuanJi.llm.feedback.FeedbackService feedbackService;
    private final XuanJi.llm.feedback.FinetuneExportService finetuneExportService;
    private final XuanJi.llm.render.ReportDataCollector reportCollector;

    /** SSE 对话用虚拟线程池（长连接流式，不占 Servlet 工作线程）。 */
    private static final ExecutorService SSE_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    /** 供应商清单（控制台 AI 设置下拉 + 能力矩阵）。 */
    @GetMapping("/providers")
    public List<Map<String, Object>> providers() {
        return llmService.providers();
    }

    /** 当前 LLM 配置。 */
    @GetMapping("/config")
    public LlmConfig config() {
        return llmService.getConfig();
    }

    /** 保存 LLM 配置（整体覆盖，落 xuanji_llm_config 表）。 */
    @PostMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody LlmConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("配置不能为空");
        }
        llmService.saveConfig(config);
        return Map.of("ok", true);
    }

    /**
     * 连通性测试：用当前配置发一条最小消息，验证 key / 网络 / 模型可用。
     * 失败不抛异常，返回具体错误信息供前端展示。
     */
    @PostMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            String reply = llmService.testConnection();
            out.put("ok", true);
            out.put("reply", reply);
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return out;
    }

    /**
     * 流式对话（SSE）：body = {@code {botKey?, message}}。
     * 事件流：{@code data: {"delta":"..."}} 逐段推送，结束 {@code data: {"done":true}}，
     * 出错 {@code data: {"error":"..."}}。botKey 用于匹配机器人级人格。
     */
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String botKey = body != null ? body.getOrDefault("botKey", "") : "";
        String message = body != null ? body.getOrDefault("message", "") : "";
        SseEmitter emitter = new SseEmitter(120_000L);

        SSE_EXECUTOR.execute(() -> {
            try {
                List<LlmMessage> messages = personaService.buildMessages(botKey, null, null, message);
                llmService.chatStream(messages, null, piece -> {
                    try {
                        emitter.send(SseEmitter.event().data(Map.of("delta", piece)));
                    } catch (Exception ignored) {
                        // 客户端断开，停止推送（由外层异常退出）
                        throw new IllegalStateException("SSE 客户端已断开");
                    }
                });
                emitter.send(SseEmitter.event().data(Map.of("done", true)));
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(Map.of("error",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
                } catch (Exception ignored) {
                    // 客户端已断开，无法再发送
                }
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    // ──────────── 人格管理（三级结构化：BOT / GROUP / USER） ────────────

    /** 内置人格模版（从模版新建数据源）。 */
    @GetMapping("/persona-templates")
    public List<LlmPersona> personaTemplates() {
        return personaTemplates.all();
    }

    /** 某 bot 的全部人格行（结构化角色卡）。 */
    @GetMapping("/personas")
    public List<LlmPersona> personas(@RequestParam(required = false) String botKey) {
        return personaService.list(botKey == null ? "" : botKey);
    }

    /** 保存人格（UPSERT）：body 为结构化 LlmPersona。 */
    @PostMapping("/personas")
    public Map<String, Object> savePersona(@RequestBody LlmPersona persona) {
        if (persona == null || persona.getBotKey() == null || persona.getBotKey().isBlank()) {
            throw new IllegalArgumentException("botKey 不能为空");
        }
        personaService.set(persona);
        return Map.of("ok", true);
    }

    /** 删除人格行。 */
    @DeleteMapping("/personas/{id}")
    public Map<String, Object> deletePersona(@PathVariable Long id) {
        personaService.delete(id);
        return Map.of("ok", true);
    }

    // ──────────── 用量统计 + 群级限额 ────────────

    /** 用量总览：今日总用量 + 各 bot 用量。 */
    @GetMapping("/usage/overview")
    public Map<String, Object> usageOverview() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> bots = quotaService.botTodayUsage();
        long total = bots.stream().mapToLong(b -> ((Number) b.get("tokens")).longValue()).sum();
        out.put("totalToday", total);
        out.put("bots", bots);
        return out;
    }

    /** 某 bot 的各群用量 + 限额 + 近 7 天趋势。 */
    @GetMapping("/usage/groups")
    public List<Map<String, Object>> usageGroups(@RequestParam String botKey,
                                                 @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> quotas = quotaService.listQuotas(botKey);
        // 合并：未设限额的群也要出现在列表里（今日用量 > 0 的）
        List<Map<String, Object>> groups = new ArrayList<>(quotas);
        Map<String, Object> byGroup = new LinkedHashMap<>();
        for (Map<String, Object> q : quotas) {
            byGroup.put(String.valueOf(q.get("groupId")), q);
        }
        // 补充今日有用量但未设限额的群
        jdbcQueryForGroups(botKey, byGroup, groups);
        for (Map<String, Object> g : groups) {
            String gid = String.valueOf(g.get("groupId"));
            g.put("trend", quotaService.groupTrend(botKey, gid, days));
        }
        return groups;
    }

    private void jdbcQueryForGroups(String botKey, Map<String, Object> byGroup, List<Map<String, Object>> out) {
        try {
            List<Map<String, Object>> used = quotaService.groupsWithUsageToday(botKey);
            for (Map<String, Object> u : used) {
                String gid = String.valueOf(u.get("groupId"));
                if (!byGroup.containsKey(gid)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("groupId", gid);
                    row.put("dailyLimit", 0L);
                    row.put("todayUsed", u.get("tokens"));
                    row.put("updatedAt", null);
                    out.add(row);
                    byGroup.put(gid, row);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** 设置群每日限额（limit 0 = 解除限制）。 */
    @PostMapping("/group-quota")
    public Map<String, Object> setGroupQuota(@RequestBody Map<String, Object> body) {
        String botKey = String.valueOf(body.getOrDefault("botKey", ""));
        String groupId = String.valueOf(body.getOrDefault("groupId", ""));
        long limit = body.get("dailyLimit") == null ? 0 : ((Number) body.get("dailyLimit")).longValue();
        if (botKey.isBlank() || groupId.isBlank()) {
            throw new IllegalArgumentException("botKey/groupId 不能为空");
        }
        quotaService.setLimit(botKey, groupId, limit);
        return Map.of("ok", true);
    }

    // ──────────── 记忆（显式记住） ────────────

    /** 某维度记忆列表。 */
    @GetMapping("/memory")
    public List<Map<String, Object>> memory(@RequestParam String botKey,
                                            @RequestParam(required = false) String groupId,
                                            @RequestParam(required = false) String userId) {
        return memoryService.list(botKey, groupId, userId);
    }

    /** 新增记忆。 */
    @PostMapping("/memory")
    public Map<String, Object> saveMemory(@RequestBody Map<String, String> body) {
        memoryService.saveDetail(
                body.getOrDefault("botKey", ""),
                body.get("groupId"), body.get("userId"),
                body.getOrDefault("key", ""),
                body.getOrDefault("value", ""), 0);
        return Map.of("ok", true);
    }

    /** 删除记忆。 */
    @DeleteMapping("/memory/{id}")
    public Map<String, Object> deleteMemory(@PathVariable Long id) {
        memoryService.delete(id);
        return Map.of("ok", true);
    }

    // ──────────── 用户画像（P1.6：全量消息认知） ────────────

    /** 用户画像列表（按消息数降序）。 */
    @GetMapping("/user-profiles")
    public List<Map<String, Object>> userProfiles(@RequestParam(required = false) String botKey,
                                                  @RequestParam(required = false) String groupId) {
        return profileService.list(botKey, groupId);
    }

    /** 删除用户画像。 */
    @DeleteMapping("/user-profiles")
    public Map<String, Object> deleteUserProfile(@RequestParam String botKey,
                                                 @RequestParam String groupId,
                                                 @RequestParam String userId) {
        profileService.delete(botKey, groupId, userId);
        return Map.of("ok", true);
    }

    // ──────────── 主动搭话（P1.6：冷场检测 + 活跃气氛） ────────────

    /** 主动搭话记录（审计）。 */
    @GetMapping("/proactive-logs")
    public List<Map<String, Object>> proactiveLogs(@RequestParam(required = false) String botKey,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return proactiveService.logs(botKey, limit);
    }

    /** 主动搭话测试：对指定群立即触发一次（需开启主动开关）。 */
    @PostMapping("/proactive/test")
    public Map<String, Object> proactiveTest(@RequestBody Map<String, String> body) {
        String type = proactiveService.proactiveOnce(
                body.getOrDefault("botKey", ""),
                body.getOrDefault("groupId", ""));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", type != null);
        out.put("type", type);
        if (type == null) {
            out.put("error", "未触发：请确认主动搭话开关已开启、该群今日活跃过且满足频控");
        }
        return out;
    }

    // ──────────── LLM 工具（P2：@LlmTool Function Calling） ────────────

    /** 已注册的 LLM 工具清单（前端「AI 工具」页数据源）。 */
    @GetMapping("/tools")
    public List<Map<String, Object>> tools() {
        return toolRegistry.definitions().stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.name());
            m.put("description", d.description());
            m.put("descriptionZh", d.descriptionZh());
            m.put("confirm", d.confirm());
            m.put("source", d.source());
            m.put("parameters", d.parameters());
            return m;
        }).toList();
    }

    // ──────────── MCP 服务管理（P3） ────────────

    /** MCP server 列表。 */
    @GetMapping("/mcp")
    public List<Map<String, Object>> mcpList(@RequestParam(required = false) String botKey) {
        return mcpService.list(botKey);
    }

    /** 注册/更新 MCP server。 */
    @PostMapping("/mcp")
    public Map<String, Object> mcpRegister(@RequestBody Map<String, Object> body) {
        mcpService.register(
                String.valueOf(body.getOrDefault("botKey", "")),
                String.valueOf(body.getOrDefault("name", "")),
                String.valueOf(body.getOrDefault("url", "")),
                (String) body.get("description"),
                Boolean.TRUE.equals(body.get("whitelist")),
                body.get("enabled") == null || Boolean.TRUE.equals(body.get("enabled")));
        return Map.of("ok", true);
    }

    /** 删除 MCP server（断开并移除工具）。 */
    @DeleteMapping("/mcp")
    public Map<String, Object> mcpDelete(@RequestParam String botKey, @RequestParam String name) {
        mcpService.delete(botKey, name);
        return Map.of("ok", true);
    }

    /** 连接 MCP server：拉取工具并注册进 ToolRegistry。 */
    @PostMapping("/mcp/connect")
    public Map<String, Object> mcpConnect(@RequestBody Map<String, String> body) {
        int count = mcpService.connect(body.getOrDefault("botKey", ""), body.getOrDefault("name", ""));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("tools", count);
        return out;
    }

    /** 断开 MCP server：移除其工具。 */
    @PostMapping("/mcp/disconnect")
    public Map<String, Object> mcpDisconnect(@RequestBody Map<String, String> body) {
        mcpService.disconnect(body.getOrDefault("botKey", ""), body.getOrDefault("name", ""));
        return Map.of("ok", true);
    }

    // ──────────── 知识库（P4） ────────────

    /** 上传文本到知识库。 */
    @PostMapping("/kb/upload")
    public Map<String, Object> kbUpload(@RequestBody Map<String, String> body) {
        long id = kbService.addText(body.getOrDefault("botKey", ""),
                body.getOrDefault("name", "未命名文档"),
                body.getOrDefault("content", ""));
        return Map.of("ok", true, "id", id);
    }

    /** 上传文件到知识库（txt/md/csv/json 等 UTF-8 文本，multipart）。 */
    @PostMapping("/kb/upload-file")
    public Map<String, Object> kbUploadFile(@RequestParam("botKey") String botKey,
                                            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        try {
            long id = kbService.addFile(botKey, file.getOriginalFilename(), file.getBytes());
            return Map.of("ok", true, "id", id, "name", file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            return Map.of("ok", false, "error", e.getMessage());
        } catch (Exception e) {
            return Map.of("ok", false, "error", "上传失败: " + e.getMessage());
        }
    }

    /** 知识库文档列表。 */
    @GetMapping("/kb")
    public List<Map<String, Object>> kbList(@RequestParam(required = false) String botKey) {
        return kbService.list(botKey == null ? "" : botKey);
    }

    /** 删除知识库文档。 */
    @DeleteMapping("/kb/{id}")
    public Map<String, Object> kbDelete(@PathVariable Long id) {
        kbService.delete(id);
        return Map.of("ok", true);
    }

    /** 知识库检索（测试问答）。 */
    @GetMapping("/kb/search")
    public Map<String, Object> kbSearch(@RequestParam String botKey, @RequestParam String q) {
        return Map.of("result", kbService.search(botKey, q, 5));
    }

    // ──────────── AI 审核（P4） ────────────

    /** 审核拦截记录。 */
    @GetMapping("/audit/logs")
    public List<Map<String, Object>> auditLogs(@RequestParam(defaultValue = "100") int limit) {
        return auditService.logs(limit);
    }

    // ──────────── AI 日报（P4） ────────────

    /** 日报配置列表。 */
    @GetMapping("/summary/config")
    public List<Map<String, Object>> summaryConfigs(@RequestParam String botKey) {
        return summaryService.configs(botKey);
    }

    /** 保存日报配置（新增/修改：同 botKey+groupId 即覆盖）。 */
    @PostMapping("/summary/config")
    public Map<String, Object> summarySave(@RequestBody Map<String, Object> body) {
        summaryService.setConfig(
                String.valueOf(body.getOrDefault("botKey", "")),
                String.valueOf(body.getOrDefault("groupId", "")),
                Boolean.TRUE.equals(body.get("enabled")),
                body.get("hour") == null ? 22 : ((Number) body.get("hour")).intValue(),
                body.get("minute") == null ? 0 : ((Number) body.get("minute")).intValue(),
                Boolean.TRUE.equals(body.get("imageMode")));
        return Map.of("ok", true);
    }

    /** 删除日报配置。 */
    @PostMapping("/summary/config/delete")
    public Map<String, Object> summaryDelete(@RequestBody Map<String, String> body) {
        summaryService.deleteConfig(body.getOrDefault("botKey", ""), body.getOrDefault("groupId", ""));
        return Map.of("ok", true);
    }

    /** 手动生成日报（并返回内容）。 */
    @PostMapping("/summary/generate")
    public Map<String, Object> summaryGenerate(@RequestBody Map<String, String> body) {
        String content = summaryService.generate(body.getOrDefault("botKey", ""), body.getOrDefault("groupId", ""));
        return Map.of("ok", content != null && !content.startsWith("日报生成失败"), "content", content);
    }

    /** 日报历史。 */
    @GetMapping("/summary/history")
    public List<Map<String, Object>> summaryHistory(@RequestParam(defaultValue = "50") int limit) {
        return summaryService.history(limit);
    }

    // ════════════ 供应商 / 模型管理（多供应商多模型） ════════════

    /** 供应商列表。 */
    @GetMapping("/providers-config")
    public List<Map<String, Object>> providerList() {
        return providerService.listProviders();
    }

    /** 保存供应商（有 id=更新，无 id=新增）。 */
    @PostMapping("/providers-config")
    public Map<String, Object> providerSave(@RequestBody Map<String, Object> body) {
        Long id = body.get("id") == null ? null : ((Number) body.get("id")).longValue();
        long savedId = providerService.saveProvider(id,
                String.valueOf(body.getOrDefault("name", "")),
                String.valueOf(body.getOrDefault("providerType", "openai")),
                String.valueOf(body.getOrDefault("baseUrl", "")),
                String.valueOf(body.getOrDefault("apiKey", "")),
                body.get("status") == null ? 1 : ((Number) body.get("status")).intValue());
        return Map.of("ok", true, "id", savedId);
    }

    /** 删除供应商（级联删其模型）。 */
    @DeleteMapping("/providers-config/{id}")
    public Map<String, Object> providerDelete(@PathVariable Long id) {
        providerService.deleteProvider(id);
        return Map.of("ok", true);
    }

    /** 某供应商的模型列表。 */
    @GetMapping("/providers-config/models")
    public List<Map<String, Object>> modelList(@RequestParam Long providerId) {
        return providerService.listModels(providerId);
    }

    /** 添加模型。 */
    @PostMapping("/providers-config/models")
    public Map<String, Object> modelSave(@RequestBody Map<String, Object> body) {
        long id = providerService.saveModel(
                ((Number) body.get("providerId")).longValue(),
                String.valueOf(body.getOrDefault("modelName", "")),
                String.valueOf(body.getOrDefault("capabilities", "")));
        return Map.of("ok", true, "id", id);
    }

    /** 删除模型。 */
    @DeleteMapping("/providers-config/models/{id}")
    public Map<String, Object> modelDelete(@PathVariable Long id) {
        providerService.deleteModel(id);
        return Map.of("ok", true);
    }

    /** 从 OpenAI 兼容供应商拉取模型列表。 */
    @PostMapping("/providers-config/{id}/fetch-models")
    public Map<String, Object> fetchModels(@PathVariable Long id) {
        Object result = providerService.fetchModels(id);
        return result instanceof Map<?, ?> m
                ? Map.of("ok", true, "models", m.get("models"))
                : Map.of("ok", false, "error", String.valueOf(result));
    }

    /** 供应商的 API Key 列表。 */
    @GetMapping("/providers-config/keys")
    public List<Map<String, Object>> keyList(@RequestParam Long providerId) {
        return providerService.listKeys(providerId);
    }

    /** 添加供应商 API Key。 */
    @PostMapping("/providers-config/keys")
    public Map<String, Object> keySave(@RequestBody Map<String, Object> body) {
        long id = providerService.saveKey(
                ((Number) body.get("providerId")).longValue(),
                String.valueOf(body.getOrDefault("apiKey", "")),
                String.valueOf(body.getOrDefault("remark", "")));
        return Map.of("ok", true, "id", id);
    }

    /** 删除供应商 API Key。 */
    @DeleteMapping("/providers-config/keys/{id}")
    public Map<String, Object> keyDelete(@PathVariable Long id) {
        providerService.deleteKey(id);
        return Map.of("ok", true);
    }

    // ════════════ 图文卡片渲染（HtmlRenderService） ════════════

    /** 可用渲染模板列表。 */
    @GetMapping("/render/templates")
    public List<String> renderTemplates() {
        return renderService.templateIds();
    }

    /**
     * 渲染模板为 PNG（预览/测试）。
     *
     * @param body templateId（必填）；data（可选 JSON，覆盖模板数据）；botKey/groupId（可选，daily-report 用真实群数据时传）
     * @return base64 PNG 或错误
     */
    @PostMapping("/render/preview")
    public Map<String, Object> renderPreview(@RequestBody Map<String, Object> body) {
        String templateId = String.valueOf(body.getOrDefault("templateId", ""));
        Map<String, Object> data;
        if ("daily-report".equals(templateId)) {
            String botKey = String.valueOf(body.getOrDefault("botKey", ""));
            String groupId = String.valueOf(body.getOrDefault("groupId", ""));
            data = reportCollector.collectDailyReport(
                    botKey, groupId,
                    String.valueOf(body.getOrDefault("groupName", "")),
                    String.valueOf(body.getOrDefault("summary", "")));
        } else {
            Object raw = body.get("data");
            data = raw instanceof Map<?, ?> m
                    ? m.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            e -> String.valueOf(e.getKey()), Map.Entry::getValue))
                    : Map.of();
        }
        try {
            byte[] png = renderService.render(templateId, data);
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("ok", true);
            out.put("templateId", templateId);
            out.put("size", png.length);
            out.put("base64", java.util.Base64.getEncoder().encodeToString(png));
            return out;
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ════════════ P2-F 用户反馈（👍/👎）════════════

    /** 提交一条用户反馈：score=1 点赞 / -1 点踩；replyText 为被评价的 AI 回复。 */
    @PostMapping("/feedback")
    public Map<String, Object> feedback(@RequestBody Map<String, Object> body) {
        try {
            String botKey = String.valueOf(body.getOrDefault("botKey", ""));
            String groupId = body.get("groupId") == null ? "" : String.valueOf(body.get("groupId"));
            String userId = body.get("userId") == null ? "" : String.valueOf(body.get("userId"));
            String replyText = body.get("replyText") == null ? "" : String.valueOf(body.get("replyText"));
            int score = body.get("score") instanceof Number n ? n.intValue() : 0;
            if (score != 1 && score != -1) {
                return Map.of("ok", false, "error", "score 只能为 1（👍）或 -1（👎）");
            }
            feedbackService.record(botKey, groupId, userId, replyText, score);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    // ════════════ P3-G 微调数据集导出 ════════════

    /** 导出某机器人的微调数据集（JSONL，OpenAI / DeepSeek 微调格式）。 */
    @GetMapping("/finetune/export")
    public Map<String, Object> finetuneExport(@RequestParam(name = "botKey", required = false) String botKey,
                                              @RequestParam(name = "limit", required = false) Integer limit) {
        try {
            return finetuneExportService.export(botKey == null ? "" : botKey, limit == null ? 50 : limit);
        } catch (Exception e) {
            return Map.of("samples", 0, "jsonl", "", "error",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
