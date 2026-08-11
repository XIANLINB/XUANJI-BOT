package XuanJi.console.controller;

import XuanJi.console.service.AuditService;
import XuanJi.core.web.XuanJiApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 系统控制接口：重启框架 / 获取启动脚本。
 *
 * <p>重启采用<b>异步退出</b>：HTTP 立刻返回 ack（让前端弹提示），后台 2 秒后关闭 Spring 上下文；
 * 外部 {@code start.sh} 守护脚本检测到进程退出后会重新拉起新进程。
 *
 * <p>鉴权（双因素）：
 * <ol>
 *   <li>cookie 登录态（控制台访问口令已经登录）</li>
 *   <li>二次确认：请求体 {@code {"confirm": "RESTART"}} 必须精确匹配（防误触）</li>
 * </ol>
 *
 * <p>注意：此接口只在 console 已认证的会话中可用（XuanJiApi 鉴权），不会绕过访问口令。
 */
@Slf4j
@XuanJiApi
@RestController
@RequestMapping("/console/system")
public class SystemController implements ApplicationContextAware {

    /** 二次确认串（必须精确匹配，包括大小写）。 */
    private static final String CONFIRM_KEY = "RESTART";

    private final AuditService auditService;
    private ApplicationContext applicationContext;

    public SystemController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 一键重启框架：二次输入 RESTART 后异步退出 Spring 容器，由 start.sh 守护脚本拉起。
     */
    @PostMapping("/restart")
    public Map<String, Object> restart(@RequestBody(required = false) Map<String, Object> body,
                                       HttpServletRequest req) {
        String confirm = body == null ? "" : String.valueOf(body.getOrDefault("confirm", ""));
        if (!CONFIRM_KEY.equals(confirm)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("msg", "二次确认失败：必须在请求体 {\"confirm\":\"RESTART\"} 中精确输入 RESTART");
            return err;
        }

        String remoteIp = req == null ? "?" : req.getRemoteAddr();
        log.warn("[System] 重启请求已确认: confirm=RESTART, remote={}", remoteIp);
        auditService.record("SYSTEM_RESTART", "用户确认重启框架: remote=" + remoteIp, req);

        // 异步关闭：2 秒后让响应先回到前端，让用户看到「重启中」提示，再让 Spring 退出
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "xuanji-restart-shutdown");
            t.setDaemon(true);
            return t;
        }).schedule(() -> {
            try {
                log.warn("[System] 开始优雅关闭 Spring 容器（2 秒倒计时已结束）");
                if (applicationContext != null) {
                    // 关闭后会触发 JVM 退出钩子；外部 start.sh 拉起新进程
                    int exitCode = org.springframework.boot.SpringApplication.exit(applicationContext, () -> 0);
                    log.warn("[System] SpringApplication.exit 返回 exitCode={}", exitCode);
                    if (exitCode != 0) System.exit(exitCode);
                } else {
                    System.exit(0);
                }
            } catch (Exception e) {
                log.error("[System] 关闭 Spring 容器异常", e);
                System.exit(1);
            }
        }, 2, TimeUnit.SECONDS);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("msg", "重启指令已接受，2 秒后关闭 Spring 容器；请确保 start.sh 守护脚本已启动");
        ok.put("nextStep", "等待 5~10 秒后用控制台访问口令重新登录");
        return ok;
    }

    /**
     * 获取标准启动脚本内容（前端可一键复制/下载）。
     */
    @GetMapping(value = "/start-script", produces = MediaType.TEXT_PLAIN_VALUE)
    public String startScript() throws IOException {
        // 优先读取用户工作目录的 start.sh（如果存在表示用户已自定义）
        Path customScript = Paths.get("start.sh");
        if (Files.exists(customScript)) {
            return Files.readString(customScript, StandardCharsets.UTF_8);
        }
        // 否则返回内置的标准脚本
        ClassPathResource res = new ClassPathResource("scripts/start.sh");
        if (!res.exists()) {
            return "# 标准启动脚本未找到，请联系框架维护者\n";
        }
        return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * 写入标准启动脚本到工作目录。
     */
    @PostMapping("/start-script/write")
    public Map<String, Object> writeStartScript(HttpServletRequest req) throws IOException {
        ClassPathResource res = new ClassPathResource("scripts/start.sh");
        String content = StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8);
        Path target = Paths.get("start.sh");
        Files.writeString(target, content, StandardCharsets.UTF_8);
        // Linux/macOS 设置可执行权限
        try {
            target.toFile().setExecutable(true, false);
        } catch (Exception ignored) {
            // Windows 不需要
        }
        auditService.record("SYSTEM_INSTALL_SCRIPT", "写入标准 start.sh 到工作目录", req);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("status", "ok");
        ok.put("path", target.toAbsolutePath().toString());
        ok.put("msg", "已写入 " + target.toAbsolutePath() + "（Linux/macOS 已设置可执行权限，Windows 可忽略）");
        return ok;
    }
}