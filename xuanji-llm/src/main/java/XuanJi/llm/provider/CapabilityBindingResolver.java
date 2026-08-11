package XuanJi.llm.provider;

import XuanJi.api.llm.LlmCredentials;
import XuanJi.llm.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 能力绑定解析器（P2 统一寻址）—— 把「能力选择」的绑定配置解析为统一的
 * {@code (providerType, credentials, model)} 列表，供图片理解/图像生成/TTS 等能力复用。
 *
 * <p>统一寻址规则（三个能力共用）：
 * <ol>
 *   <li>多选绑定优先：{@code ["12:glm-4v", "3:gpt-4o"]}（providerId:modelName），逐个解析，按序尝试</li>
 *   <li>单值绑定兜底：providerId + modelBinding（旧配置兼容）</li>
 *   <li>每个供应商按多 API Key 展开（轮询容灾）</li>
 * </ol>
 *
 * <p>消除 ImageUnderstand / ImageGen / TTS 三处重复的 bindingFromString / bindingFromProvider。
 */
@Slf4j
@Component
public class CapabilityBindingResolver {

    /** 统一绑定结果：providerType（供应商类型）+ 凭据 + 模型名。 */
    public record CapBinding(String providerType, LlmCredentials creds, String model) {}

    private final ProviderService providerService;

    public CapabilityBindingResolver(ProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * 解析能力绑定列表：多选优先 → 单值兜底 → 空列表。
     *
     * @param cfg               当前配置
     * @param multiBindings     多选绑定（"providerId:modelName" 列表）
     * @param singleProviderId  单值绑定供应商 id（可为 null）
     * @param singleModelBinding 单值绑定模型名（可为 null）
     * @param fallbackModel     模型名缺省时兜底
     */
    public List<CapBinding> resolve(LlmConfig cfg,
                                    List<String> multiBindings,
                                    Long singleProviderId,
                                    String singleModelBinding,
                                    String fallbackModel) {
        List<CapBinding> out = new ArrayList<>();
        if (multiBindings != null && !multiBindings.isEmpty()) {
            for (String b : multiBindings) {
                out.addAll(fromString(b, fallbackModel));
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        if (singleProviderId != null && singleProviderId > 0) {
            out.addAll(fromProvider(singleProviderId, singleModelBinding, fallbackModel));
            if (!out.isEmpty()) {
                return out;
            }
        }
        return out;
    }

    /** 解析 "providerId:modelName" 字符串为绑定列表；供应商不存在/格式非法返回空。 */
    public List<CapBinding> fromString(String binding, String fallbackModel) {
        int idx = binding == null ? -1 : binding.indexOf(':');
        if (idx <= 0) {
            return List.of();
        }
        long providerId;
        try {
            providerId = Long.parseLong(binding.substring(0, idx));
        } catch (NumberFormatException e) {
            return List.of();
        }
        return fromProvider(providerId, binding.substring(idx + 1), fallbackModel);
    }

    /** 供应商绑定 → 按多 Key 展开为绑定列表。 */
    public List<CapBinding> fromProvider(long providerId, String model, String fallbackModel) {
        Map<String, Object> p = providerService.getProvider(providerId);
        if (p == null) {
            return List.of();
        }
        String type = String.valueOf(p.get("providerType"));
        String useModel = (model != null && !model.isBlank()) ? model : fallbackModel;
        String baseUrl = String.valueOf(p.get("baseUrl"));
        List<String> keys = providerService.enabledKeys(providerId);
        if (keys.isEmpty()) {
            keys = List.of(p.get("apiKey") == null ? "" : String.valueOf(p.get("apiKey")));
        }
        List<CapBinding> out = new ArrayList<>();
        for (String key : keys) {
            out.add(new CapBinding(type, new LlmCredentials(baseUrl, key), useModel));
        }
        return out;
    }
}
