package XuanJi.llm.multimodal;

import XuanJi.api.llm.LlmCapability;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmCredentials;
import XuanJi.api.llm.LlmProvider;
import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import XuanJi.llm.provider.LlmProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图片理解服务 —— 用智谱 GLM-4V（免费视觉模型）理解图片。
 *
 * <p>凭据与对话供应商独立（LlmConfig 的 vision* 字段）。提供
 * {@code image_understand} 工具：群聊/Agent 收到图片或用户要求看图时，
 * 模型调用本工具传图片 URL 获取描述。
 */
@Slf4j
@Service
public class ImageUnderstandService {

    /** 每个模型重试次数（限流/服务端错误时，含首次共 MAX_RETRIES+1 次尝试，然后切换下一个模型）。 */
    private static final int MAX_RETRIES = 2;

    /** 重试基础等待毫秒（指数退避：500ms → 1000ms）。 */
    private static final long RETRY_BASE_DELAY_MS = 500;

    private final LlmConfigStore configStore;
    private final LlmProviderRegistry registry;
    private final XuanJi.llm.provider.CapabilityBindingResolver bindingResolver;

    public ImageUnderstandService(LlmConfigStore configStore, LlmProviderRegistry registry,
                                  XuanJi.llm.provider.CapabilityBindingResolver bindingResolver) {
        this.configStore = configStore;
        this.registry = registry;
        this.bindingResolver = bindingResolver;
    }

    /**
     * 理解图片：imageUrl + 问题 → 描述文本。
     *
     * <p>多模型容灾：候选模型列表 = 主模型(visionModel) + 备选(visionFallbackModels)。
     * 每个模型重试 {@value #MAX_RETRIES} 次仍失败则自动切换下一个，全部失败返回汇总错误。
     */
    public String understand(String imageUrl, String prompt) {
        LlmConfig cfg = configStore.get();
        List<VBinding> bindings = resolveVisionBindings(cfg);
        if (bindings.isEmpty()) {
            return "未配置视觉模型 Key，请到「AI 能力 → AI 设置」配置图片理解能力或填写智谱 GLM Key";
        }
        String resolved = resolveImage(imageUrl);
        String finalPrompt = prompt == null || prompt.isBlank() ? "请描述这张图片的内容" : prompt;
        List<String> failures = new ArrayList<>();
        Exception lastError = null;
        for (VBinding b : bindings) {
            LlmChatOptions opts = new LlmChatOptions(b.model(), 0.7, 512, null);
            int attempt = 0;
            while (true) {
                try {
                    return b.provider().vision(resolved, finalPrompt, opts, b.creds());
                } catch (Exception e) {
                    lastError = e;
                    if (isRetryable(e) && attempt < MAX_RETRIES) {
                        attempt++;
                        long delay = RETRY_BASE_DELAY_MS * (1L << (attempt - 1));
                        log.warn("[VISION] 模型 {} 繁忙，第 {}/{} 次重试，等待 {}ms: {}",
                                b.model(), attempt, MAX_RETRIES, delay, e.getMessage());
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return "图片理解失败: " + friendlyError(e);
                        }
                        continue;
                    }
                    // 本模型已重试够次数 → 切下一个
                    log.warn("[VISION] 模型 {} 失败，切换下一个: {}", b.model(), e.getMessage());
                    failures.add(b.model() + "(" + shortError(e) + ")");
                    break;
                }
            }
        }
        log.warn("[VISION] 全部视觉模型失败: {}", failures);
        return "图片理解失败: 已尝试 " + String.join("、", failures) + " 均不可用。"
                + friendlyError(lastError);
    }

    /** 视觉绑定：供应商实现 + 凭据 + 模型名。 */
    private record VBinding(LlmProvider provider, LlmCredentials creds, String model) {}

    /** 解析视觉绑定列表（统一寻址：多选优先 → 单值绑定，按多 Key 展开）。 */
    private List<VBinding> resolveVisionBindings(LlmConfig cfg) {
        List<VBinding> out = new ArrayList<>();
        for (XuanJi.llm.provider.CapabilityBindingResolver.CapBinding cb :
                bindingResolver.resolve(cfg, LlmCapability.IMAGE_UNDERSTAND, cfg.getVisionBindings(),
                        cfg.getVisionProviderId(), cfg.getVisionModelBinding(), "glm-4.6v-flash")) {
            LlmProvider bound = registry.byId(cb.providerType());
            if (bound == null) {
                log.warn("[VISION] 跳过未知供应商类型 {}: 无对应实现，请检查供应商配置", cb.providerType());
                continue;
            }
            if (!bound.capabilities().contains(LlmCapability.IMAGE_UNDERSTAND)) {
                log.warn("[VISION] 跳过供应商类型 {}: 该类型未声明图片理解能力（模型 {} 不会生效），"
                        + "请到「供应商管理」将供应商类型改为支持视觉的供应商（如智谱 GLM）",
                        cb.providerType(), cb.model());
                continue;
            }
            out.add(new VBinding(bound, cb.creds(), cb.model()));
        }
        return out;
    }

    /** 是否可重试：限流（429）或服务端错误（5xx）。 */
    private boolean isRetryable(Exception e) {
        if (e instanceof org.springframework.web.client.RestClientResponseException re) {
            int sc = re.getStatusCode().value();
            return sc == 429 || sc >= 500;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("429") || msg.contains("Too Many") || msg.contains("访问量过大"));
    }

    /** 错误 → 用户可读提示（429 限流单独友好化）。 */
    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("429") || msg.contains("Too Many")
                || msg.contains("访问量过大") || msg.contains("1305"))) {
            return "智谱视觉模型繁忙（限流），请稍后再试，或在「AI 设置 → 多模态」调整视觉模型";
        }
        return msg == null ? e.getClass().getSimpleName() : msg;
    }

    /** 错误 → 简短标识（附加在失败模型名后）。 */
    private String shortError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        int end = msg.indexOf('\n');
        String first = end > 0 ? msg.substring(0, end) : msg;
        return first.length() > 60 ? first.substring(0, 60) : first;
    }

    /**
     * 图片引用解析：本地文件路径 → base64 编码字符串（GLM-4V 的 image_url.url 支持直接传 base64）；
     * 公网 URL / data URI 原样透传。
     */
    private String resolveImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return imageUrl;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://") || imageUrl.startsWith("data:")) {
            return imageUrl;
        }
        try {
            java.io.File f = new java.io.File(imageUrl);
            if (!f.exists()) {
                log.warn("[VISION] 本地图片文件不存在: {}", imageUrl);
                return imageUrl;
            }
            byte[] bytes = java.nio.file.Files.readAllBytes(f.toPath());
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("[VISION] 读取本地图片失败: {} err={}", imageUrl, e.getMessage());
            return imageUrl;
        }
    }

    @LlmTool(name = "image_understand",
            descriptionZh = "理解并描述一张图片",
            description = "理解图片内容：用户发来图片或要求描述/分析一张图片时调用。传入图片地址（公网 URL 或本地文件路径均可）和问题，返回模型对图片的描述",
            confirm = false)
    public String imageUnderstand(
            @LlmToolParam(name = "imageUrl", value = "图片地址：公网 URL 或本地文件路径均可") String imageUrl,
            @LlmToolParam(name = "prompt", value = "关于图片的问题，可选，默认让模型描述图片", required = false) String prompt) {
        if (imageUrl == null || imageUrl.isBlank()) return "请提供图片 URL";
        return understand(imageUrl.trim(), prompt);
    }
}
