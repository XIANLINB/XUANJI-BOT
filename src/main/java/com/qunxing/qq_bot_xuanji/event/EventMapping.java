package com.qunxing.qq_bot_xuanji.event;

import java.lang.annotation.*;

/**
 * 事件映射注解
 *
 * <p>标注在 {@link EventHandler} 实现类上，声明该 Handler 处理的事件类型。
 * 框架启动时自动扫描所有带此注解的 Handler，注册到 {@link EventDispatcher}。
 *
 * <h3>使用示例</h3>
 * <pre>
 * @EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE"})
 * @Component
 * public class GroupMessageHandler implements EventHandler {
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventMapping {

    /**
     * 事件类型列表
     *
     * <p>一个 Handler 可以处理多种事件类型，例如：
     * <ul>
     *   <li>GROUP_MESSAGE_CREATE — 群消息全量模式</li>
     *   <li>GROUP_AT_MESSAGE_CREATE — 群 @消息</li>
     * </ul>
     *
     * @return 事件类型字符串数组
     */
    String[] value();
}
