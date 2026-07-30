package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 标记方法为群聊消息处理器 — Shiro 风格的自由事件监听。
 *
 * <p>与 {@link Command} 不同，此注解不依赖前缀匹配，而是配合
 * {@link HandlerFilter} 实现灵活过滤（cmd/regex/groups/senders/at 等）。
 *
 * <pre>
 * @GroupMessageHandler
 * @HandlerFilter(cmd = "签到|打卡", at = AtMode.NEED)
 * public void onSign(XjGroupMessageEvent e, XjBot bot) {
 *     bot.reply(e.getSenderName() + " 签到成功！");
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GroupMessageHandler {
    /** 优先级，越小越先执行 */
    int order() default 0;
}
