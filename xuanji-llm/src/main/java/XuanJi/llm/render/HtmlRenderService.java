package XuanJi.llm.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * HTML 模板渲染截图服务 —— 用无头 Chromium（Playwright）把 HTML 模板 + 数据
 * 渲染成 PNG 图片，供图文卡片（日报/签到/战绩等）富媒体发送。
 *
 * <p><b>模板机制</b>：一个模板 = 一个 HTML 文件，放在
 * {@code data/render/templates/}（用户目录，可热替换）或 classpath
 * {@code resources/render/templates/}（内置默认）。模板内通过
 * {@code window.RENDER_DATA = {{DATA}};} 接收注入的 JSON 数据，渲染完成后
 * 设置 {@code document.body.dataset.rendered = '1'} 通知截图。
 *
 * <p><b>浏览器二进制</b>：用 {@code playwright.driver.browsers} 指向项目
 * {@code data/browser} 目录（首次需执行 {@code playwright install chromium}，
 * 见 README）。缺失时返回友好提示而非崩溃。
 *
 * <p><b>资源策略</b>：浏览器实例启动一次全程复用（懒加载），每次渲染仅
 * 新开/关闭页面，内存可控（常驻约 150MB）。
 */
@Slf4j
@Service
public class HtmlRenderService {

    /** 浏览器二进制目录（相对工作目录，Playwright driver.browsers 指向）。 */
    private static final String BROWSER_DIR = "data/browser";
    /** 用户模板目录（优先于内置模板）。 */
    private static final Path USER_TEMPLATES = Paths.get("data", "render", "templates");
    /** 内置模板在 classpath 的位置。 */
    private static final String CLASSPATH_TEMPLATES = "render/templates/";
    /** 数据注入占位符。 */
    private static final String DATA_PLACEHOLDER = "{{DATA}}";
    /** 渲染完成标记：模板 JS 渲染完设置为 body[data-rendered]="1"。 */
    private static final String RENDERED_MARK = "1";

    /** 无头浏览器实例（懒加载、全程复用）。 */
    private volatile Playwright playwright;
    private volatile Browser browser;
    private final AtomicBoolean launching = new AtomicBoolean(false);

    /** 模板 → 内容缓存（内置模板读一次，用户模板每次读最新）。 */
    private final Map<String, String> builtinTemplateCache = new java.util.concurrent.ConcurrentHashMap<>();

    private final XuanJi.llm.config.LlmConfigStore configStore;

    public HtmlRenderService(XuanJi.llm.config.LlmConfigStore configStore) {
        this.configStore = configStore;
    }

    /** 渲染总开关是否开启（AI 设置 → 图文卡片渲染）。 */
    public boolean isEnabled() {
        return configStore != null && configStore.get() != null && configStore.get().isRenderEnabled();
    }

    /** 模板是否存在（用户目录或内置）。 */
    public boolean hasTemplate(String templateId) {
        return resolveTemplateSource(templateId) != null;
    }

    /** 全部可用模板 ID 列表。 */
    public java.util.List<String> templateIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        try {
            org.springframework.core.io.Resource[] res =
                    new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                            .getResources("classpath*:" + CLASSPATH_TEMPLATES + "*.html");
            for (org.springframework.core.io.Resource r : res) {
                ids.add(stripExt(r.getFilename()));
            }
        } catch (IOException e) {
            log.warn("[RENDER] 扫描内置模板失败: {}", e.getMessage());
        }
        if (Files.isDirectory(USER_TEMPLATES)) {
            try (Stream<Path> s = Files.list(USER_TEMPLATES)) {
                s.filter(p -> p.getFileName().toString().endsWith(".html"))
                        .forEach(p -> ids.add(stripExt(p.getFileName().toString())));
            } catch (IOException e) {
                log.warn("[RENDER] 扫描用户模板失败: {}", e.getMessage());
            }
        }
        return new java.util.ArrayList<>(ids);
    }

    /**
     * 渲染模板为 PNG。
     *
     * @param templateId 模板 ID（不含 .html）
     * @param data       注入到模板的数据（序列化为 JSON 替换 {{DATA}}）
     * @return PNG 字节
     * @throws IllegalStateException 模板不存在 / 浏览器未安装 / 渲染失败
     */
    public byte[] render(String templateId, Map<String, Object> data) {
        if (!isEnabled()) {
            throw new IllegalStateException("图文卡片渲染已关闭：请到「AI 能力 → AI 设置 → 全部设置」开启「图文卡片渲染」开关");
        }
        String html = loadTemplate(templateId);
        if (html == null) {
            throw new IllegalStateException("模板不存在: " + templateId + "（可用: " + templateIds() + "）");
        }
        Browser br = ensureBrowser();
        Page page = null;
        try {
            page = br.newPage(new Browser.NewPageOptions().setViewportSize(
                    new com.microsoft.playwright.options.ViewportSize(860, 600)));
            // 数据序列化（{{DATA}} 占位替换，JSON 字符串直接内联到 <script>）
            String json = jsonEscape(data == null ? "{}" : new tools.jackson.databind.ObjectMapper().writeValueAsString(data));
            String finalHtml = html.replace(DATA_PLACEHOLDER, json);
            page.setContent(finalHtml, new Page.SetContentOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
            // 等模板 JS 渲染完成（最多 5s）
            try {
                page.waitForSelector("body[data-rendered=\"" + RENDERED_MARK + "\"]",
                        new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (Exception e) {
                // 模板未写渲染标记也能截（可能只是静态内容），静默继续
                log.debug("[RENDER] 模板 {} 未设置渲染标记，直接截图", templateId);
            }
            byte[] png = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            log.info("[RENDER] 模板渲染成功: {} → {}B", templateId, png.length);
            return png;
        } catch (Exception e) {
            log.warn("[RENDER] 模板渲染失败: {}: {}", templateId, e.getMessage());
            throw new IllegalStateException("模板渲染失败: " + e.getMessage());
        } finally {
            if (page != null) {
                page.close();
            }
        }
    }

    // ──────────── 模板加载 ────────────

    /** 加载模板内容：优先用户目录（data/render/templates），回退内置 classpath。 */
    String loadTemplate(String templateId) {
        String safe = templateId.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (safe.isEmpty()) {
            return null;
        }
        Path userFile = USER_TEMPLATES.resolve(safe + ".html");
        if (Files.isRegularFile(userFile)) {
            try {
                return Files.readString(userFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("[RENDER] 读取用户模板失败: {}", e.getMessage());
            }
        }
        // 内置模板缓存
        return builtinTemplateCache.computeIfAbsent(safe, id -> {
            try {
                ClassPathResource r = new ClassPathResource(CLASSPATH_TEMPLATES + id + ".html");
                if (r.exists()) {
                    return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.warn("[RENDER] 读取内置模板失败: {}", e.getMessage());
            }
            return null;
        });
    }

    /** 模板是否存在（用户目录或内置）。 */
    private String resolveTemplateSource(String templateId) {
        String safe = templateId.replaceAll("[^A-Za-z0-9_\\-]", "");
        if (Files.isRegularFile(USER_TEMPLATES.resolve(safe + ".html"))) {
            return "user";
        }
        try {
            if (new ClassPathResource(CLASSPATH_TEMPLATES + safe + ".html").exists()) {
                return "builtin";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String stripExt(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    // ──────────── 浏览器生命周期 ────────────

    /** 获取浏览器实例（懒加载 + 复用）。浏览器未安装时返回友好错误。 */
    private Browser ensureBrowser() {
        if (browser != null && !browser.isConnected()) {
            browser = null;
        }
        if (browser != null) {
            return browser;
        }
        if (launching.compareAndSet(false, true)) {
            try {
                String executable = findChromiumExecutable();
                if (executable != null) {
                    // 已下载的本地 Chromium：直接用可执行文件 + 跳过下载。
                    // 浏览器路径通过系统属性告知 Playwright（Java 侧读取）；进程 env 若已设
                    // PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 则跳过 install 校验（由启动脚本提供，
                    // 否则首次会自动下载到 data/browser）。不覆盖 CreateOptions env（避免 driver 子进程缺环境）。
                    System.setProperty("playwright.driver.browsers", Paths.get(BROWSER_DIR).toAbsolutePath().toString());
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setExecutablePath(java.nio.file.Paths.get(executable))
                            .setArgs(java.util.List.of("--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage")));
                    log.info("[RENDER] 无头 Chromium 已启动（本地二进制）: {}", executable);
                } else {
                    // 未找到本地二进制：让 Playwright 用其浏览器目录（首次会尝试下载）
                    Path browserPath = Paths.get(BROWSER_DIR).toAbsolutePath();
                    try {
                        Files.createDirectories(browserPath);
                    } catch (IOException ignored) {
                    }
                    System.setProperty("playwright.driver.browsers", browserPath.toString());
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                            .setHeadless(true)
                            .setArgs(java.util.List.of("--no-sandbox", "--disable-gpu", "--disable-dev-shm-usage")));
                    log.info("[RENDER] 无头 Chromium 已启动（Playwright 管理）");
                }
            } catch (Exception e) {
                log.warn("[RENDER] Chromium 启动失败: {}", e.getMessage(), e);
                throw new IllegalStateException(
                        "渲染浏览器未就绪：请先安装 Chromium（在项目根目录执行 "
                        + "\"mvn exec:java -D exec.mainClass=com.microsoft.playwright.CLI "
                        + "-D exec.args=\\\"install chromium\\\"\"）。详情: " + e.getMessage());
            } finally {
                launching.set(false);
            }
        } else {
            // 另一个线程正在启动，等待
            for (int i = 0; i < 100; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (browser != null && browser.isConnected()) {
                    return browser;
                }
            }
        }
        return browser;
    }

    /**
     * 在项目 data/browser 目录下查找已下载的 Chromium 可执行文件。
     * Playwright 目录结构：chromium-<rev>/chrome-win64/chrome.exe（Windows）、chrome-linux/chrome（Linux）。
     */
    private String findChromiumExecutable() {
        Path base = Paths.get(BROWSER_DIR).toAbsolutePath();
        if (!Files.isDirectory(base)) {
            return null;
        }
        try (Stream<Path> s = Files.list(base)) {
            for (Path dir : s.toList()) {
                String name = dir.getFileName().toString();
                if (!name.startsWith("chromium")) {
                    continue;
                }
                // Windows
                Path win = dir.resolve("chrome-win64/chrome.exe");
                if (Files.isRegularFile(win)) {
                    return win.toString();
                }
                Path winHeadless = dir.resolve("chrome-headless-shell-win64/headless_shell.exe");
                if (Files.isRegularFile(winHeadless)) {
                    return winHeadless.toString();
                }
                // Linux
                Path lin = dir.resolve("chrome-linux/chrome");
                if (Files.isRegularFile(lin)) {
                    return lin.toString();
                }
                Path linHeadless = dir.resolve("chrome-headless-shell-linux64/chrome-headless-shell");
                if (Files.isRegularFile(linHeadless)) {
                    return linHeadless.toString();
                }
            }
        } catch (IOException e) {
            log.warn("[RENDER] 扫描浏览器目录失败: {}", e.getMessage());
        }
        return null;
    }

    /** JSON 字符串转义（用于内联到 <script> 且不破坏 HTML）。 */
    private static String jsonEscape(String json) {
        if (json == null) {
            return "{}";
        }
        // 先把 </script> 序列避免提前闭合，再转义基本控制字符由 ObjectMapper 保证
        return json.replace("</", "<\\/");
    }
}
