package dev.xuanji.core.demo;

import dev.xuanji.api.annotation.Command;
import dev.xuanji.api.annotation.Arg;
import dev.xuanji.api.annotation.XuanjiPlugin;
import dev.xuanji.core.command.CommandRegistry;
import dev.xuanji.core.permission.PermissionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 璇玑演示插件 — 仅通过 @Command 注册到 CommandRegistry 工作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@XuanjiPlugin(id = "xuanji.demo", name = "璇玑演示", version = "1.0.0")
public class DemoPlugin {

    private final CommandRegistry commandRegistry;
    private final PermissionService permissionService;

    @PostConstruct
    void init() {
        commandRegistry.register(this);
    }

    @Command("ping")
    public String cmdPing() {
        return "pong! 璇玑框架运行正常";
    }

    @Command(value = "hello", alias = "hi")
    public String cmdHello(@Arg("名字") String name) {
        return "你好, " + (name != null ? name : "世界") + "!";
    }

    @Command("帮助")
    public String cmdHelp() {
        return "璇玑演示命令: ping | hello <名字> | 帮助";
    }

    @Command(value = "黑名单", description = "添加黑名单（仅主人）")
    public String cmdBlacklist(@Arg("用户ID") String userId) {
        if (!permissionService.isMaster("Yolo.H")) return "权限不足，仅机器人主人可用";
        permissionService.addBlacklist("framework", "user", userId, "手动添加", "console");
        return "已将 " + userId + " 加入黑名单";
    }
}
