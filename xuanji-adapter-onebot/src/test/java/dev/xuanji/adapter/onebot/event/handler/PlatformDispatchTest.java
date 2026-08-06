package dev.xuanji.adapter.onebot.event.handler;

import dev.xuanji.api.annotation.*;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.sdk.bot.Bot;
import dev.xuanji.sdk.event.GroupMessageEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证插件按平台选择订阅：handler 级 platforms 优先，插件级 @XuanjiPlugin(platforms=) 兜底，
 * 两者皆空 = 全平台。覆盖 QQ 专属 / OneBot 专属 / 默认全平台 / 未知平台 / 插件级默认 五种场景。
 */
public class PlatformDispatchTest {

    /** handler 级 + 默认(全平台) */
    @XuanjiPlugin(id = "t1", name = "t1", version = "1.0")
    public static class HandlerLevelPlugin {
        @GroupMessage(platforms = {"qq"})
        @MessageFilter(cmd = "go")
        public String qqOnly() { return "QQ"; }

        @GroupMessage(platforms = {"onebot"})
        @MessageFilter(cmd = "go")
        public String onebotOnly() { return "ONEBOT"; }

        @GroupMessage
        @MessageFilter(cmd = "go")
        public String bothDefault() { return "BOTH"; }
    }

    /** 插件级默认 + handler 覆盖 */
    @XuanjiPlugin(id = "t2", name = "t2", version = "1.0", platforms = {"qq"})
    public static class PluginLevelPlugin {
        @GroupMessage
        @MessageFilter(cmd = "pd")
        public String pluginDefault() { return "PDEF"; }

        @GroupMessage(platforms = {"onebot"})
        @MessageFilter(cmd = "pd")
        public String handlerOverride() { return "POVR"; }
    }

    private static void ctx(String platform) {
        CommandRegistry.setContext("k", "g", "m", "u", (GroupMessageEvent) null, (Bot) null, platform);
    }

    @Test
    void handlerLevelQqOnlyFiresOnQq() {
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(new HandlerLevelPlugin(), "t1");
        ctx("qq");
        assertEquals("QQ", cr.executeGroupMessage("go"));
        CommandRegistry.clearContext();
    }

    @Test
    void handlerLevelOnebotOnlyFiresOnOnebot() {
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(new HandlerLevelPlugin(), "t1");
        ctx("onebot");
        assertEquals("ONEBOT", cr.executeGroupMessage("go"));
        CommandRegistry.clearContext();
    }

    @Test
    void defaultHandlerFiresOnUnknownPlatform() {
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(new HandlerLevelPlugin(), "t1");
        ctx("discord");
        assertEquals("BOTH", cr.executeGroupMessage("go"));
        CommandRegistry.clearContext();
    }

    @Test
    void pluginLevelDefaultRestrictsToDeclaredPlatform() {
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(new PluginLevelPlugin(), "t2");
        // onebot 平台：pluginDefault 继承插件 qq → 被拦截；handlerOverride 显式 onebot → 命中
        ctx("onebot");
        assertEquals("POVR", cr.executeGroupMessage("pd"));
        CommandRegistry.clearContext();
    }

    @Test
    void pluginLevelDefaultFiresOnDeclaredPlatform() {
        CommandRegistry cr = new CommandRegistry(null);
        cr.register(new PluginLevelPlugin(), "t2");
        // qq 平台：pluginDefault(qq) 命中，且早于 handlerOverride(onebot) → 返回 PDEF
        ctx("qq");
        assertEquals("PDEF", cr.executeGroupMessage("pd"));
        CommandRegistry.clearContext();
    }
}
