package XuanJi.console.market;

import XuanJi.core.config.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插件市场配置 — 存取于框架库 {@code xuanji_config} 全局键。
 *
 * <p>配置项：
 * <ul>
 *   <li>{@code market.enabled} — 是否启用插件市场</li>
 *   <li>{@code market.repo_url} — 市场公开 git 仓库地址（默认官方仓库）</li>
 *   <li>{@code market.git_user} / {@code market.git_token} — 管理员 git 凭据（上传/审核/上架的写操作需要）</li>
 * </ul>
 *
 * <p>普通用户浏览/下载走仓库 raw 匿名读取（无需凭据）；只有开发者上传、管理员审核需要写凭据。
 */
@Slf4j
@Component
public class MarketSettings {

    public static final String K_ENABLED = "market.enabled";
    public static final String K_REPO_URL = "market.repo_url";
    public static final String K_GIT_USER = "market.git_user";
    public static final String K_GIT_TOKEN = "market.git_token";

    /** 官方插件市场仓库（用户可改为自己的市场仓库） */
    public static final String DEFAULT_REPO_URL = "https://cnb.cool/XuanJiBot/XuanJiBot-plugins.git";
    /** 公开索引文件路径（仓库内） */
    public static final String INDEX_PATH = "index.json";
    /** 待审提交目录（仓库内） */
    public static final String PENDING_DIR = ".pending";
    /** 已上架插件目录（仓库内） */
    public static final String PLUGINS_DIR = "plugins";

    private final ConfigService configService;

    public MarketSettings(ConfigService configService) {
        this.configService = configService;
    }

    public boolean isEnabled() {
        return "true".equalsIgnoreCase(configService.getGlobalConfig().get(K_ENABLED));
    }

    public String getRepoUrl() {
        String v = configService.getGlobalConfig().get(K_REPO_URL);
        return (v == null || v.isBlank()) ? DEFAULT_REPO_URL : v.trim();
    }

    public String getGitUser() {
        String v = configService.getGlobalConfig().get(K_GIT_USER);
        return (v == null || v.isBlank()) ? "cnb" : v.trim();
    }

    public String getGitToken() {
        return configService.getGlobalConfig().get(K_GIT_TOKEN);
    }

    public boolean hasCredential() {
        String t = getGitToken();
        return t != null && !t.isBlank();
    }

    /** 保存配置；token 为空或掩码串时不覆盖已有值。 */
    public void save(String repoUrl, String gitUser, String gitToken, Boolean enabled) {
        String url = (repoUrl == null || repoUrl.isBlank()) ? DEFAULT_REPO_URL : repoUrl.trim();
        configService.setGlobal(K_REPO_URL, url);
        configService.setGlobal(K_GIT_USER, (gitUser == null || gitUser.isBlank()) ? "cnb" : gitUser.trim());
        if (gitToken != null && !gitToken.isBlank() && !gitToken.startsWith("*")) {
            configService.setGlobal(K_GIT_TOKEN, gitToken.trim());
        }
        if (enabled != null) {
            configService.setGlobal(K_ENABLED, String.valueOf(enabled));
        }
        log.info("[PluginMarket] 配置已保存 repoUrl={} gitUser={} enabled={}", url, getGitUser(), enabled);
    }

    /** 查看配置（token 仅返回是否已配置，不回显原文）。 */
    public Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", isEnabled());
        m.put("repoUrl", getRepoUrl());
        m.put("gitUser", getGitUser());
        m.put("hasToken", hasCredential());
        return m;
    }
}
