package dev.xuanji.core.demo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.XuanjiPlugin;
import dev.xuanji.api.json.Json;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 演示插件 — 走旧 EventMapping 入口，内部用 @Command 匹配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE", "C2C_MESSAGE_CREATE"})
@XuanjiPlugin(id = "xuanji.demo", name = "璇玑演示", version = "1.0.0")
public class DemoPlugin implements EventHandler {

    private final MessageSender messageSender;
    private final CommandRegistry commandRegistry;

    @PostConstruct
    void init() {
        commandRegistry.register(this);
    }

    @Override
    public String getEventType() { return "DEMO_PLUGIN"; }

    @Override
    public void handle(Long robotId, String envType, ObjectNode data) {
        String content = data.path("content").asText("");
        if (content == null || content.isBlank()) return;
        // 如果 @Command 匹配了就不走旧 switch 逻辑
        String result = commandRegistry.execute(content.trim());
        if (result != null) {
            log.info("[DemoPlugin] @Command: {} → {}", content.trim(), result);
        }
    }

    // ==================== @Command 方法 ====================

    @Command("文本")
    public String cmdText() {
        return "这是一条文本消息\n时间: " + java.time.LocalDateTime.now();
    }

    @Command("ping")
    public String cmdPing() {
        return "pong! 璇玑框架运行正常";
    }

    @Command(value = "hello", description = "打招呼", alias = "hi")
    public String cmdHello(@Arg("名字") String name) {
        return "你好, " + (name != null ? name : "世界") + "!";
    }

    @Command(value = "帮助", alias = "help")
    public String cmdHelp() {
        return """
                璇玑演示命令:
                文本 | ping | hello <名字>
                帮助""";
    }
}
