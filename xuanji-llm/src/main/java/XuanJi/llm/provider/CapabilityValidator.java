package XuanJi.llm.provider;

import XuanJi.api.llm.LlmCapability;
import XuanJi.api.llm.LlmChatOptions;
import XuanJi.api.llm.LlmCredentials;
import XuanJi.api.llm.LlmProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 能力契约启动期校验器。
 *
 * <p>「能力契约单一事实来源」改造（方案 A + 方案 D 校验器部分）的收口组件：
 * 在 Spring 容器刷新完成后，对每个已注册的 {@link LlmProvider} 做「声明能力 ∩ 实现方法」一致性校验，
 * 不一致仅打印告警（<b>不阻断启动</b>），把原本要等到运行时调用才抛 {@link UnsupportedOperationException} 的隐患在启动期暴露出来。
 *
 * <p>校验双向：
 * <ol>
 *   <li>声明了某能力却无对应实现（如声明 {@code IMAGE_UNDERSTAND} 但 {@code vision()} 仍是默认抛异常实现）→ 告警；</li>
 *   <li>实现了某能力方法却未在 {@code capabilities()} 声明（如覆盖了 {@code vision()} 却没声明 {@code IMAGE_UNDERSTAND}）→ 告警，
 *       否则控制台看不到该功能入口。</li>
 * </ol>
 *
 * <p>注意：本校验器<b>不做</b>完整 registry 间接层（方案 D 仅取其「校验」部分），不引入新的能力路由间接层。
 * 仅 {@link LlmProvider} 接口中 default 抛 {@link UnsupportedOperationException} 的方法（{@code vision}/{@code imageGen}）
 * 参与校验；其余富能力（TTS/STT/EMBEDDING 等）接口暂无对应方法，属前瞻声明，仅打印 INFO 提示，不告警。
 */
@Component
public class CapabilityValidator {

    private static final Logger log = LoggerFactory.getLogger(CapabilityValidator.class);

    /** 能力 → 必须由供应商覆盖的方法（接口默认实现抛 UnsupportedOperationException）。 */
    private static final Map<LlmCapability, String> OVERRIDE_METHODS = Map.of(
            LlmCapability.IMAGE_UNDERSTAND, "vision",
            LlmCapability.IMAGE_GEN, "imageGen"
    );

    private final LlmProviderRegistry registry;

    public CapabilityValidator(LlmProviderRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    public void validate() {
        List<LlmProvider> providers = registry.all();
        if (providers.isEmpty()) {
            log.info("[Capability] 未注册任何 LlmProvider，跳过能力契约校验");
            return;
        }
        log.info("[Capability] 启动期能力契约校验开始，共 {} 个供应商", providers.size());
        for (LlmProvider p : providers) {
            validateProvider(p);
        }
        log.info("[Capability] 启动期能力契约校验结束");
    }

    private void validateProvider(LlmProvider p) {
        Set<LlmCapability> declared = p.capabilities();
        String id = p.id();

        if (declared == null || declared.isEmpty()) {
            log.warn("[Capability] 供应商 {} 未声明任何能力（capabilities() 为空），至少应声明 CHAT", id);
            return;
        }
        if (!declared.contains(LlmCapability.CHAT)) {
            log.warn("[Capability] 供应商 {} 未声明基础能力 CHAT，对话功能可能不可用", id);
        }

        // 方向一：声明了能力，必须有对应实现
        for (Map.Entry<LlmCapability, String> e : OVERRIDE_METHODS.entrySet()) {
            LlmCapability cap = e.getKey();
            String methodName = e.getValue();
            if (declared.contains(cap) && !isOverridden(p, methodName)) {
                log.warn("[Capability] 供应商 {} 声明能力 {} 但 {}(...) 仍是默认实现（运行时将抛 UnsupportedOperationException），契约不一致",
                        id, cap, methodName);
            }
        }

        // 方向二：实现了方法，必须声明对应能力（否则控制台不可见）
        for (Map.Entry<LlmCapability, String> e : OVERRIDE_METHODS.entrySet()) {
            LlmCapability cap = e.getKey();
            String methodName = e.getValue();
            if (!declared.contains(cap) && isOverridden(p, methodName)) {
                log.warn("[Capability] 供应商 {} 实现了 {}(...) 但未在 capabilities() 声明 {}，功能对控制台不可见",
                        id, methodName, cap);
            }
        }

        // 前向声明但接口暂无对应方法的富能力：仅 INFO 提示（框架尚未支持，属合理前瞻声明）
        for (LlmCapability cap : declared) {
            if (cap != LlmCapability.CHAT && !OVERRIDE_METHODS.containsKey(cap)) {
                log.info("[Capability] 供应商 {} 声明能力 {} 但 LlmProvider 接口暂无对应实现方法，暂无法校验实现（前瞻声明）", id, cap);
            }
        }
    }

    /** 判断 provider 是否覆盖了接口的 default 实现（即该方法的 declaringClass 不是 {@link LlmProvider} 本身）。 */
    private boolean isOverridden(LlmProvider p, String methodName) {
        try {
            Method m = findMethod(p.getClass(), methodName);
            return m != null && m.getDeclaringClass() != LlmProvider.class;
        } catch (Exception ex) {
            log.debug("[Capability] 反射检查 {}.{} 失败: {}", p.id(), methodName, ex.getMessage());
            return false;
        }
    }

    private Method findMethod(Class<?> clazz, String name) throws NoSuchMethodException {
        if ("vision".equals(name)) {
            return clazz.getMethod("vision", String.class, String.class, LlmChatOptions.class, LlmCredentials.class);
        }
        return clazz.getMethod("imageGen", String.class, LlmChatOptions.class, LlmCredentials.class);
    }
}
