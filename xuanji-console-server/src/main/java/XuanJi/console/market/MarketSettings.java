package XuanJi.console.market;

import XuanJi.core.config.ConfigService;
import XuanJi.core.security.CredentialCipher;
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
 *   <li>{@code market.repo_url} — 市场公开 git 仓库地址（加密存储；前端不回显，防抓包暴露）</li>
 *   <li>{@code market.upload_token} — <b>开发者上传令牌</b>（git 写凭据，仅用于提交插件到待审区）</li>
 *   <li>{@code market.admin_token} — <b>管理员审核令牌</b>（通过/拒绝待审提交必需，进入审核台需验证）</li>
 * </ul>
 *
 * <p>安全边界：
 * <ul>
 *   <li>普通用户浏览/安装走框架后端代理下载，<b>前端永远不接触仓库真实地址</b></li>
 *   <li>上传令牌 / 管理员令牌 / 仓库地址 均经 {@link CredentialCipher} 加密落库</li>
 *   <li>审核台操作（通过/拒绝）必须携带管理员令牌，后端比对通过才执行</li>
 * </ul>
 */
@Slf4j
@Component
public class MarketSettings {

    public static final String K_ENABLED = "market.enabled";
    public static final String K_REPO_URL = "market.repo_url";
    public static final String K_UPLOAD_TOKEN = "market.upload_token";
    public static final String K_ADMIN_TOKEN = "market.admin_token";

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
        String v = readConfig(K_REPO_URL);
        return (v == null || v.isBlank()) ? DEFAULT_REPO_URL : v.trim();
    }

    /** 开发者上传令牌（git 写凭据，仅上传用）。 */
    public String getUploadToken() {
        return readConfig(K_UPLOAD_TOKEN);
    }

    /** 管理员审核令牌。 */
    public String getAdminToken() {
        return readConfig(K_ADMIN_TOKEN);
    }

    public boolean hasUploadToken() {
        return notBlank(getUploadToken());
    }

    public boolean hasAdminToken() {
        return notBlank(getAdminToken());
    }

    /** 校验是否为管理员令牌（审核台操作鉴权）。 */
    public boolean verifyAdminToken(String token) {
        return token != null && !token.isBlank() && token.equals(getAdminToken());
    }

    /** 保存配置；空值或掩码串（*）不覆盖已有值。敏感项加密落库。 */
    public void save(String repoUrl, String uploadToken, String adminToken, Boolean enabled) {
        String url = (repoUrl == null || repoUrl.isBlank()) ? DEFAULT_REPO_URL : repoUrl.trim();
        if (!url.startsWith("*")) {
            setConfig(K_REPO_URL, CredentialCipher.encrypt(url));
        }
        if (uploadToken != null && !uploadToken.isBlank() && !uploadToken.startsWith("*")) {
            setConfig(K_UPLOAD_TOKEN, CredentialCipher.encrypt(uploadToken.trim()));
        }
        if (adminToken != null && !adminToken.isBlank() && !adminToken.startsWith("*")) {
            setConfig(K_ADMIN_TOKEN, CredentialCipher.encrypt(adminToken.trim()));
        }
        if (enabled != null) {
            configService.setGlobal(K_ENABLED, String.valueOf(enabled));
        }
        log.info("[PluginMarket] 配置已保存 enabled={} 仓库URL已更新={}", isEnabled(),
                (repoUrl != null && !repoUrl.startsWith("*")));
    }

    /** 查看配置（敏感项仅返回是否已配置，不回显原文；仓库地址掩码显示）。 */
    public Map<String, Object> view() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", isEnabled());
        m.put("repoUrl", maskUrl(getRepoUrl()));
        m.put("hasUploadToken", hasUploadToken());
        m.put("hasAdminToken", hasAdminToken());
        return m;
    }

    private static String maskUrl(String url) {
        if (url == null || url.isBlank()) return "";
        // 仅保留协议与域名，路径打码（防抓包看到仓库归属/路径）
        try {
            int scheme = url.indexOf("://");
            if (scheme < 0) return "***";
            String head = url.substring(0, scheme + 3);
            String rest = url.substring(scheme + 3);
            int slash = rest.indexOf('/');
            String host = slash < 0 ? rest : rest.substring(0, slash);
            return head + host + "/***";
        } catch (Exception e) {
            return "***";
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String readConfig(String key) {
        String v = configService.getGlobalConfig().get(key);
        if (v == null || v.isBlank()) return null;
        return v.startsWith(CredentialCipher.PREFIX) ? CredentialCipher.decrypt(v) : v;
    }

    private void setConfig(String key, String value) {
        configService.setGlobal(key, value);
    }
}
