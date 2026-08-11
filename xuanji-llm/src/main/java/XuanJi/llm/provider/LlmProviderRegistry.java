package XuanJi.llm.provider;

import XuanJi.api.llm.LlmProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LLM 供应商注册表 —— 收集容器内所有 {@link LlmProvider} Bean（含插件注册的自定义供应商），
 * 按 {@code id()} 索引。控制台展示与运行时选型都走本注册表。
 */
@Component
public class LlmProviderRegistry {

    private final Map<String, LlmProvider> providers;

    public LlmProviderRegistry(List<LlmProvider> providerBeans) {
        this.providers = providerBeans.stream()
                .collect(Collectors.toConcurrentMap(LlmProvider::id, Function.identity(),
                        (a, b) -> a, ConcurrentHashMap::new));
    }

    /** 全部供应商（按 displayName 排序，控制台下拉稳定）。 */
    public List<LlmProvider> all() {
        return providers.values().stream()
                .sorted(java.util.Comparator.comparing(LlmProvider::displayName))
                .toList();
    }

    /** 按 id 取供应商；未知 id 返回 null。 */
    public LlmProvider byId(String id) {
        return providers.get(id);
    }
}
