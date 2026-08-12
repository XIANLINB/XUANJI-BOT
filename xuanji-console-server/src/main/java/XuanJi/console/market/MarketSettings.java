package XuanJi.console.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 插件市场配置 — 固定内置，无需设置页。
 *
 * <p>设计：
 * <ul>
 *   <li><b>默认启用</b>：插件市场开箱即用，无需任何配置</li>
 *   <li><b>仓库地址固定</b>：官方插件市场仓库（CNB 公开 git 仓库）</li>
 *   <li><b>令牌加密写死</b>：上传令牌（所有使用者上传用）+ 审核令牌（仅管理员）经
 *       AES-256-GCM 加密写死在 {@link MarketTokens}，源码无明文</li>
 *   <li><b>仓库地址不出后端</b>：浏览/下载走框架代理端点，前端不接触真实仓库地址</li>
 * </ul>
 */
@Slf4j
@Component
public class MarketSettings {

    /** 官方插件市场仓库（CNB 公开 git 仓库）。 */
    public static final String DEFAULT_REPO_URL = "https://cnb.cool/XuanJiBot/XuanJiBot-plugins.git";
    /** 待审提交目录（仓库内） */
    public static final String PENDING_DIR = ".pending";
    /** 已上架插件目录（仓库内）：plugins/&lt;pluginId&gt;/plugin.json + plugins/&lt;pluginId&gt;/&lt;version&gt;/xxx.jar */
    public static final String PLUGINS_DIR = "plugins";
    /** 每插件专属清单文件名（覆盖式，只保留当前上架版本）。 */
    public static final String PLUGIN_MANIFEST = "plugin.json";

    public boolean isEnabled() {
        return true; // 默认启用，无需开关
    }

    public String getRepoUrl() {
        return DEFAULT_REPO_URL;
    }

    /** 开发者上传令牌（所有框架实例使用者上传插件用）。 */
    public String getUploadToken() {
        return MarketTokens.uploadToken();
    }

    /** 管理员审核令牌（仅管理员持有）。 */
    public String getAdminToken() {
        return MarketTokens.adminToken();
    }

    public boolean hasUploadToken() {
        return true;
    }

    public boolean hasAdminToken() {
        return true;
    }

    /** 校验是否为管理员令牌（审核台操作鉴权）。 */
    public boolean verifyAdminToken(String token) {
        return token != null && !token.isBlank() && token.equals(getAdminToken());
    }
}
