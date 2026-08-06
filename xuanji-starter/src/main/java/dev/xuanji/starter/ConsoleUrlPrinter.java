package dev.xuanji.starter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动就绪打印控制台地址 — 重启后直接在控制台看到前端入口，不必翻配置。
 *
 * <p>端口从 {@code server.port} 读（固定端口 8668 等）；SERVER__PORT=0 随机端口时提示按实际端口访问。
 */
@Slf4j
@Component
public class ConsoleUrlPrinter implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment env;

    public ConsoleUrlPrinter(Environment env) {
        this.env = env;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            String port = env.getProperty("server.port", "8080");
            String line = "=".repeat(58);
            System.out.println("\n" + line);
            System.out.println("  [璇玑] 控制台已就绪，请访问:");
            if ("0".equals(port)) {
                System.out.println("    端口为随机分配（SERVER__PORT=0），请按日志中 Tomcat 实际端口访问:");
                System.out.println("    控制台: http://localhost:<实际端口>/xuanji/console/");
            } else {
                System.out.println("    🖥 控制台:   http://localhost:" + port + "/xuanji/console/");
                System.out.println("    🩺 健康检查: http://localhost:" + port + "/xuanji/api/console/health");
            }
            System.out.println(line + "\n");
            log.info("[璇玑] 控制台地址: http://localhost:{}/xuanji/console/", port);
        } catch (Exception e) {
            log.debug("[璇玑] 控制台地址打印失败: {}", e.getMessage());
        }
    }
}
