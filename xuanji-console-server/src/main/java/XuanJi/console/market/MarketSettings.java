package XuanJi.console.market;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 插件市场配置 — 双仓库模型（固定内置，无需设置页）。
 *
 * <p>仓库职责分离：
 * <ul>
 *   <li><b>审核仓库（review）</b> XuanJiBot-plugin：开发者用<b>上传令牌</b> push 待审提交
 *       （.pending/&lt;id&gt;/meta.json + jar），状态「审核中」</li>
 *   <li><b>正式仓库（release）</b> XuanJiBot-plugins：管理员审核通过后，用<b>管理员令牌</b>
 *       push 已上架插件（plugins/&lt;pluginId&gt;/plugin.json + jar），状态「已上架」；
 *       已上架插件可被管理员<b>下架</b>（改写 plugin.json 状态为「已下架」并附理由）</li>
 * </ul>
 *
 * <p>设计：
 * <ul>
 *   <li><b>默认启用</b>：插件市场开箱即用，无需任何配置</li>
 *   <li><b>仓库地址固定</b>：两个官方仓库（CNB 公开 git 仓库）地址写死</li>
 *   <li><b>令牌：审核仓库令牌写死</b>：审核仓库上传令牌经 AES-256-GCM 加密写死在
 *       {@link MarketTokens}，源码无明文；<b>正式仓库管理员令牌不存储</b>——审核台每次输入的
 *       令牌由后端实时去正式仓库做「写文件 → push → 删除 → push」往返验证，不以相等比较鉴权</li>
 *   <li><b>仓库地址不出后端</b>：浏览/下载走框架代理端点，前端不接触真实仓库地址</li>
 * </ul>
 */
@Slf4j
@Component
public class MarketSettings {

    /** 审核仓库（开发者提交待审）。 */
    public static final String REVIEW_REPO_URL = "https://cnb.cool/XuanJiBot/XuanJiBot-plugin.git";
    /** 正式仓库（已上架插件）。 */
    public static final String RELEASE_REPO_URL = "https://cnb.cool/XuanJiBot/XuanJiBot-plugins.git";

    /** 待审提交目录（审核仓库内）：.pending/&lt;id&gt;/meta.json + jar */
    public static final String PENDING_DIR = ".pending";
    /** 已上架插件目录（正式仓库内）：plugins/&lt;pluginId&gt;/plugin.json + plugins/&lt;pluginId&gt;/&lt;version&gt;/xxx.jar */
    public static final String PLUGINS_DIR = "plugins";
    /** 每插件专属清单文件名（覆盖式，只保留当前上架版本）。 */
    public static final String PLUGIN_MANIFEST = "plugin.json";

    /** 本地工作副本子目录名（data/plugin-market/ 下） */
    public static final String REVIEW_DIR = "review-repo";
    public static final String RELEASE_DIR = "release-repo";

    /** 插件状态（中文，存于 meta.json / plugin.json） */
    public static final String STATUS_PENDING = "审核中";
    public static final String STATUS_APPROVED = "已上架";
    public static final String STATUS_REJECTED = "拒绝上架";
    public static final String STATUS_DELISTED = "已下架";

    public boolean isEnabled() {
        return true; // 默认启用，无需开关
    }

    // —— 审核仓库（review）——
    public String getReviewRepoUrl() {
        return REVIEW_REPO_URL;
    }

    /** 开发者提交审核令牌（push 到审核仓库 .pending/）。 */
    public String getReviewUploadToken() {
        return MarketTokens.reviewToken();
    }

    // —— 正式仓库（release）——
    public String getReleaseRepoUrl() {
        return RELEASE_REPO_URL;
    }

    public boolean hasUploadToken() {
        return true;
    }
}
