package XuanJi.llm.render;

import XuanJi.api.llm.LlmReplySink;
import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.tool.LlmToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 卡片渲染工具 —— 让 AI 把数据渲染成图文卡片发到群里。
 *
 * <p>模板机制：{@code templateId} 对应 {@code data/render/templates/<id>.html} 或
 * 内置 {@code render/templates/<id>.html}。内置模板：daily-report（群日报卡片）。
 *
 * <p>数据来源：模板 {@code daily-report} 自动收集当前群真实数据；其他模板
 * 用 AI 传入的 {@code data} JSON。渲染 → PNG → 富媒体发送。
 */
@Slf4j
@Service
public class CardTools {

    private final HtmlRenderService renderService;
    private final ReportDataCollector reportCollector;
    private final List<LlmReplySink> sinks;

    public CardTools(HtmlRenderService renderService,
                     ReportDataCollector reportCollector,
                     List<LlmReplySink> sinks) {
        this.renderService = renderService;
        this.reportCollector = reportCollector;
        this.sinks = sinks;
    }

    @LlmTool(name = "render_card",
            descriptionZh = "渲染图文卡片并发送到群",
            description = "渲染图文卡片并发送到当前会话。模板 daily-report=群日报卡片（自动收集当前群消息数/活跃成员/AI总结）。" +
                    "用户要求'发个日报卡片/今日总结图/生成卡片'时调用。可传 templateId 和可选 data(JSON)。返回是否成功",
            confirm = false)
    public String renderCard(
            @LlmToolParam(name = "templateId", value = "模板 ID（当前支持 daily-report），留空用 daily-report", required = false) String templateId,
            @LlmToolParam(name = "data", value = "可选 JSON 数据（覆盖模板默认数据），留空则自动收集群数据", required = false) String data,
            @LlmToolParam(name = "caption", value = "配文（可选，随图片一起发的文字说明）", required = false) String caption,
            LlmToolContext ctx) {
        if (ctx == null || ctx.groupId() == null || ctx.groupId().isBlank()) {
            return "当前仅在群聊中支持渲染卡片";
        }
        String tid = (templateId == null || templateId.isBlank()) ? "daily-report" : templateId;
        if (!renderService.isEnabled()) {
            return "图文卡片渲染未开启：请到「AI 能力 → AI 设置 → 全部设置」打开「图文卡片渲染」开关";
        }
        if (!renderService.hasTemplate(tid)) {
            return "模板不存在: " + tid + "（可用: " + String.join(", ", renderService.templateIds()) + "）";
        }
        try {
            Map<String, Object> renderData;
            if ("daily-report".equals(tid)) {
                renderData = reportCollector.collectDailyReport(
                        ctx.botKey(), ctx.groupId(), null, null);
            } else {
                renderData = parseData(data);
            }
            byte[] png = renderService.render(tid, renderData);
            if (png == null || png.length == 0) {
                return "卡片渲染结果为空";
            }
            for (LlmReplySink sink : sinks) {
                sink.replyImageFile(ctx.event(), png,
                        caption == null || caption.isBlank() ? "📊 " + tid + " 卡片" : caption);
            }
            return "✅ 已发送 " + tid + " 卡片（" + png.length + "B）";
        } catch (Exception e) {
            log.warn("[RENDER-CARD] 渲染卡片失败: {}", e.getMessage());
            return "卡片渲染失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseData(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object v = new tools.jackson.databind.ObjectMapper().readValue(json, Object.class);
            if (v instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return Map.of();
        } catch (Exception e) {
            log.warn("[RENDER-CARD] data JSON 解析失败，用空数据: {}", e.getMessage());
            return Map.of();
        }
    }
}
