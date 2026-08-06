package dev.xuanji.adapter.qqbot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * QQ 适配器可插拔开关。
 *
 * <p>标注在 QQ 适配器的任意 Spring Bean 上，使该 Bean 仅在 {@code xuanji.qqbot.enabled=true}
 * （默认，缺省即视为 true）时才装配。设 {@code xuanji.qqbot.enabled=false} 时整个 QQ 模块
 * 不加载，应用仍可正常启动（此时由 core 提供 {@code DefaultBotContextManager} 兜底）。
 *
 * <p>与 OneBot 的 {@code @ConditionalOnProperty(xuanji.onebot.enabled)} 对称，区别是 QQ 默认启用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "xuanji.qqbot", name = "enabled", matchIfMissing = true)
public @interface ConditionalOnQqbotEnabled {
}
