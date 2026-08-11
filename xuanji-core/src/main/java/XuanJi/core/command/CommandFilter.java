package XuanJi.core.command;

import XuanJi.api.annotation.AtMode;
import XuanJi.api.annotation.Command;
import XuanJi.api.annotation.MediaMode;
import XuanJi.api.annotation.MediaType;
import XuanJi.api.annotation.MessageFilter;

/**
 * {@code @Command} 注解 → {@link MessageFilter} 适配器（P2-F 语法糖）。
 *
 * <p>把 {@code @Command} 的过滤字段原样映射为 {@link MessageFilter} 语义，
 * 使命令路由逻辑（dispatch / matchFilter / argsAfterCommand）零改动复用。
 *
 * <p><b>必须无状态</b>：scope=BOTH 时同一实例会同时注入群聊/私聊两条注册，
 * 共享同实例，不得持有任何可变字段。
 */
public final class CommandFilter implements MessageFilter {

    private final Command cmd;

    public CommandFilter(Command cmd) {
        this.cmd = cmd;
    }

    @Override public String cmd() { return !cmd.cmd().isEmpty() ? cmd.cmd() : cmd.value(); }
    @Override public AtMode at() { return cmd.at(); }
    @Override public String[] groups() { return cmd.groups(); }
    @Override public String[] senders() { return cmd.senders(); }
    @Override public String startWith() { return cmd.startWith(); }
    @Override public String endWith() { return cmd.endWith(); }
    @Override public boolean invert() { return cmd.invert(); }
    @Override public String[] roles() { return cmd.roles(); }
    @Override public String[] platforms() { return cmd.platforms(); }
    @Override public MediaMode media() { return cmd.media(); }
    @Override public MediaType[] mediaTypes() { return cmd.mediaTypes(); }

    @Override
    public Class<? extends java.lang.annotation.Annotation> annotationType() {
        return MessageFilter.class;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommandFilter that)) return false;
        return cmd.equals(that.cmd);
    }

    @Override
    public int hashCode() { return cmd.hashCode(); }

    @Override
    public String toString() { return "CommandFilter(" + cmd() + ", media=" + media() + ")"; }
}
