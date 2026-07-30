package dev.xuanji.plugin.demo;

import dev.xuanji.api.annotation.*;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * 璇玑演示插件 — 独立 PF4J 插件 jar。
 *
 * <p>插件开发者只需：
 * <ol>
 *   <li>引入 xuanji-sdk 依赖</li>
 *   <li>继承 {@link Plugin} 类</li>
 *   <li>在 MANIFEST.MF 中声明 Plugin-Id / Plugin-Class / Plugin-Version</li>
 *   <li>用注解写指令方法</li>
 *   <li>打包 jar → 放入 启动目录/plugins/ → 框架自动加载</li>
 * </ol>
 */
public class DemoPlugin extends Plugin {

    public DemoPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @XuanjiPlugin(id = "demo-plugin", name = "演示插件", version = "1.0.0")
    public static class Commands {

        @Command("ping")
        public String ping() {
            return "pong! 璇玑框架运行正常";
        }

        @Command(value = "hello", alias = "你好")
        public String hello(@Arg("名字") String name) {
            return "你好, " + (name != null ? name : "世界") + "!";
        }

        @Command("帮助")
        public String help() {
            return "璇玑插件演示: ping | hello <名字> | 帮助";
        }

        @Command("时间")
        public String time() {
            return "当前时间: " + java.time.LocalDateTime.now().toString().replace("T", " ");
        }
    }
}
