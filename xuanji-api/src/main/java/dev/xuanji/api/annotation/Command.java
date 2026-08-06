package dev.xuanji.api.annotation;

import java.lang.annotation.*;

/**
 * 命令注解 — 合并 {@link GroupMessage} / {@link PrivateMessage} 与 {@link MessageFilter} 的语法糖，
 * 让「一条消息 → 一个命令方法」降到最低样板。
 *
 * <pre>{@code
 *   // 旧写法（两个注解才够）
 *   @GroupMessage @MessageFilter(cmd = "签到")  public void sign() {...}
 *
 *   // 新写法（一个注解搞定）
 *   @Command("签到")  public void sign() {...}
 *
 *   // 默认 scope = BOTH：群聊与私聊都注册同一命令
 *   @Command("ping")  public String ping() {...}
 *
 *   // 仅私聊、带角色限制
 *   @Command(value = "管理", scope = Command.Scope.PRIVATE, roles = {"owner"})
 *   public void admin(GroupMessageEvent e, Bot bot) {...}
 * }</pre>
 *
 * <p>字段与 {@link MessageFilter} 一一对应（cmd / startWith / endWith / at / groups / senders /
 * roles / media / mediaTypes / platforms / invert），并额外提供 {@link #scope()} 与 {@link #order()}。
 * 命令词可用 {@link #value()}（简写 {@code @Command("签到")}）或 {@link #cmd()} 指定，
 * <b>两者并存时 {@code cmd} 优先</b>。
 *
 * <h3>向后兼容</h3>
 * 本注解与原生注解可并存于不同方法；但同一方法上若已标注 {@code @Command}，注册中心会
 * <b>跳过</b>其 {@code @GroupMessage} / {@code @PrivateMessage} 处理，避免重复注册。
 *
 * @see MessageFilter  过滤维度定义
 * @see OnMessage      更底层的全量消息监听（本注解是其上游命令路由封装）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {

    /** 触发命令（支持正则，如 "签到|打卡"）。空串 = 不做命令匹配（匹配所有消息）。 */
    String value() default "";

    /** 同 {@link #value()}，便于显式书写；与 {@code value} 并存时本字段优先。 */
    String cmd() default "";

    /** 触发场景：默认 {@link Scope#BOTH}（群聊 + 私聊都注册）。 */
    Scope scope() default Scope.BOTH;

    /** 优先级，同 order 内按平台命中优先级再排序。 */
    int order() default 0;

    /** @机器人模式。 */
    AtMode at() default AtMode.IGNORE;

    /** 限定群 ID（空=全部群）。 */
    String[] groups() default {};

    /** 限定发送者 member_openid。 */
    String[] senders() default {};

    /** 前缀触发。 */
    String startWith() default "";

    /** 后缀触发。 */
    String endWith() default "";

    /** 反转过滤器（满足条件时跳过）。 */
    boolean invert() default false;

    /** 限定角色（空=不限制，如 {"owner","admin"}）。 */
    String[] roles() default {};

    /** 限定平台（空=全部平台，如 {"qqbot"} / {"onebot"}）。 */
    String[] platforms() default {};

    /**
     * 富媒体模式：{@code NEED}=必须含富媒体，{@code NOT}=必须纯文本，{@code IGNORE}=不限制（默认）。
     *
     * <p>纯图片消息的 content 为空串，命令式过滤（cmd/startWith）必然 miss；
     * 用本维度即可在无文本的情况下订阅到消息。
     */
    MediaMode media() default MediaMode.IGNORE;

    /**
     * 限定富媒体类型（空=任意类型）。
     *
     * <p>声明后会触发消息链懒解析以判定具体类型；未声明则仅按附件标记判定，零解析开销。
     */
    MediaType[] mediaTypes() default {};

    /** 命令触发场景。 */
    enum Scope { GROUP, PRIVATE, BOTH }
}
