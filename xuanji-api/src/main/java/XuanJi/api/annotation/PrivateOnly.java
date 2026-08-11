package XuanJi.api.annotation;

import java.lang.annotation.*;

/**
 * 仅私聊响应 — 标注在一个消息处理方法上，表示该命令/监听器只在私聊（单聊）场景生效。
 *
 * <p>与 {@link GroupOnly} 互斥：两者都不标 = 群聊与私聊都响应（默认）；
 * 同时标注两个注解时以 {@link PrivateOnly} 为准。
 *
 * <pre>{@code
 *   // 该命令只在私聊可用，群里发不响应
 *   @Command("管理员") @PrivateOnly
 *   public void admin() {...}
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PrivateOnly {}
