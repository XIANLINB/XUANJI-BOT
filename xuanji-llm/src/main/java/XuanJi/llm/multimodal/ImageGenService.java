package XuanJi.llm.multimodal;

import XuanJi.api.llm.LlmCapability;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmCredentials;
import XuanJi.api.llm.LlmProvider;
import XuanJi.api.llm.LlmReplySink;
import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import XuanJi.llm.provider.LlmProviderRegistry;
import XuanJi.llm.tool.LlmToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文生图服务 —— 智谱 CogView-3-Flash（免费）生成图片/表情包。
 *
 * <p>复用视觉凭据（LlmConfig visionApiKey/visionBaseUrl，同一智谱账号）。
 * 提供 {@code image_gen} 工具：AI 日常对话中按需生成图片/表情包，
 * 通过 {@link LlmReplySink#replyImage} 发到当前会话。
 */
@Slf4j
@Service
public class ImageGenService {

    private final LlmConfigStore configStore;
    private final LlmProviderRegistry registry;
    private final List<LlmReplySink> sinks;
    private final XuanJi.llm.provider.CapabilityBindingResolver bindingResolver;

    public ImageGenService(LlmConfigStore configStore, LlmProviderRegistry registry,
                           List<LlmReplySink> sinks,
                           XuanJi.llm.provider.CapabilityBindingResolver bindingResolver) {
        this.configStore = configStore;
        this.registry = registry;
        this.sinks = sinks;
        this.bindingResolver = bindingResolver;
    }

    /** 生成图片：prompt → 图片公网 URL（或错误说明字符串）。 */
    public Object generate(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "请提供图片描述";
        }
        LlmConfig cfg = configStore.get();
        List<GBinding> bindings = resolveImageBindings(cfg);
        if (bindings.isEmpty()) {
            return "未配置图像生成，请到「AI 能力 → AI 设置 → 能力选择」配置图像生成模型，或填写智谱 GLM Key";
        }
        List<String> errors = new ArrayList<>();
        for (GBinding b : bindings) {
            try {
                LlmChatOptions opts = new LlmChatOptions(b.model(), 1.0, null, null);
                return b.provider().imageGen(prompt.trim(), opts, b.creds());
            } catch (Exception e) {
                errors.add(b.model() + "(" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + ")");
                log.warn("[IMAGE_GEN] 模型 {} 失败，尝试下一个: {}", b.model(), e.getMessage());
            }
        }
        return "图片生成失败: 已尝试 " + String.join("、", errors) + " 均不可用";
    }

    /** 图像生成绑定：供应商实现 + 凭据 + 模型名。 */
    private record GBinding(LlmProvider provider, LlmCredentials creds, String model) {}

    /** 解析图像生成绑定列表（统一寻址：多选优先 → 单值绑定，按多 Key 展开）。 */
    private List<GBinding> resolveImageBindings(LlmConfig cfg) {
        List<GBinding> out = new ArrayList<>();
        for (XuanJi.llm.provider.CapabilityBindingResolver.CapBinding cb :
                bindingResolver.resolve(cfg, LlmCapability.IMAGE_GEN, cfg.getImageBindings(),
                        cfg.getImageProviderId(), cfg.getImageModelBinding(), "cogview-3-flash")) {
            LlmProvider bound = registry.byId(cb.providerType());
            if (bound != null) {
                out.add(new GBinding(bound, cb.creds(), cb.model()));
            }
        }
        return out;
    }

    @LlmTool(name = "image_gen",
            descriptionZh = "AI 生成图片并发送到群",
            description = "生成一张图片并发送给当前会话：当用户要求画图/做表情包，或你想用一张图/表情包替代纯文本表达时调用。传入描述，返回是否成功发送",
            confirm = false)
    public String imageGen(
            @LlmToolParam(name = "prompt", value = "图片内容描述（文生图提示词，越具体越好，可含风格/氛围/构图）") String prompt,
            @LlmToolParam(name = "caption", value = "配图文字/说明（可选，随图发送或用作兜底文本），留空则不附带文字", required = false) String caption,
            LlmToolContext ctx) {
        Object result = generate(prompt);
        if (result instanceof String url && (url.startsWith("http://") || url.startsWith("https://"))) {
            boolean sent = false;
            String sentErr = null;
            String fallback = caption == null || caption.isBlank() ? prompt : caption;
            if (ctx.event() != null && !sinks.isEmpty()) {
                for (LlmReplySink sink : sinks) {
                    try {
                        sink.replyImage(ctx.event(), url, fallback);
                        sent = true;
                        break;
                    } catch (UnsupportedOperationException e) {
                        sentErr = "当前平台不支持发送图片";
                    } catch (Exception e) {
                        sentErr = e.getMessage();
                        log.warn("[IMAGE_GEN] 发送图片失败: {}", e.getMessage());
                    }
                }
            }
            if (sent) {
                return "已向当前会话发送生成的图片（描述：" + prompt + "）";
            }
            return "图片已生成（" + url + "），但发送失败：" + (sentErr != null ? sentErr : "无可用发送通道");
        }
        return String.valueOf(result);
    }
}