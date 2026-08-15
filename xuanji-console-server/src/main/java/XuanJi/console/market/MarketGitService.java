package XuanJi.console.market;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * 插件市场 git 仓库操作（JGit）— clone / pull / 提交 / push。
 *
 * <p>双工作副本（编译期与运行时分离）：
 * <ul>
 *   <li><b>审核仓库</b> 本地副本 {@code data/plugin-market/review-repo/}（开发者待审提交）</li>
 *   <li><b>正式仓库</b> 本地副本 {@code data/plugin-market/release-repo/}（已上架插件）</li>
 * </ul>
 *
 * <p>纯 Java 实现（JGit），发布包无需安装系统 git。
 *
 * <p>读操作（clone/pull）不需要凭据（公开仓库）；写操作（commit/push）需要对应仓库的 git 令牌。
 */
@Slf4j
@Component
public class MarketGitService {

    /** 远程默认分支（CNB 仓库默认 main；clone 时若默认分支不同会回退到自动检出）。 */
    private static final String BRANCH = "main";
    /** CNB 部署/访问令牌的 git 用户名固定为 cnb。 */
    private static final String GIT_USER = "cnb";

    /** 本地工作副本目录（相对工作目录，data/plugin-market/&lt;name&gt;/）。 */
    public Path repoDir(String name) {
        return Paths.get("data", "plugin-market", name);
    }

    /**
     * 打开本地工作副本并同步到最新（不存在则 clone 指定仓库）。
     * 读操作（clone/pull）不需要凭据（公开仓库匿名可读）。
     *
     * @param repoUrl   远程仓库地址（带 .git）
     * @param localName 本地工作副本子目录名（review-repo / release-repo）
     * @return 打开的 Git 对象（调用方负责 close）
     */
    public Git openOrPull(String repoUrl, String localName) throws Exception {
        File dir = repoDir(localName).toFile();
        if (dir.exists() && new File(dir, ".git").exists()) {
            Git git = Git.open(dir);
            try {
                git.pull().setRemote("origin")
                        .setRemoteBranchName(BRANCH)
                        .setCredentialsProvider(null)
                        .call();
            } catch (Exception e) {
                log.warn("[PluginMarket] pull 失败（继续使用本地副本）: {}", e.getMessage());
            }
            return git;
        }
        Files.createDirectories(repoDir(localName));
        log.info("[PluginMarket] 克隆市场仓库: {}", repoUrl);
        try {
            return Git.cloneRepository().setURI(repoUrl)
                    .setDirectory(dir)
                    .setBranch(BRANCH)
                    .call();
        } catch (Exception e) {
            // 默认分支非 main 时回退：不指定分支 clone
            log.warn("[PluginMarket] clone(main) 失败，尝试默认分支: {}", e.getMessage());
            return Git.cloneRepository().setURI(repoUrl)
                    .setDirectory(dir)
                    .call();
        }
    }

    /** add 全部 + commit + push（写操作，必须提供有效 git 令牌）。 */
    public void commitAndPush(Git git, String gitToken, String message) throws Exception {
        if (gitToken == null || gitToken.isBlank()) {
            throw new IllegalStateException("插件市场写操作缺少 git 令牌（上传/审核前请先在设置页配置）");
        }
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message)
                .setAuthor(GIT_USER, GIT_USER + "@noreply.cnb.cool")
                .call();
        git.push().setRemote("origin")
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, gitToken))
                .call();
        log.info("[PluginMarket] 已推送: {}", message);
    }

    /**
     * 真实令牌探测：用给定令牌对目标仓库做一次「写探针文件 → push → 删除 → push」往返。
     * 用于鉴定令牌是否具备该仓库的写访问（推送）权限，<b>不对令牌做任何相等比较或落库</b>。
     *
     * <p>流程：clone/pull 到本地副本 → 写一个随机探针文件 → 提交并 push → 删除该文件 → 提交并 push。
     * 任一环节失败（含鉴权失败）即返回 {@code false}，且本地残留会被 try-with-resources 关闭后自然丢弃。
     *
     * @param repoUrl   远程仓库地址（带 .git）
     * @param localName 本地工作副本子目录名（review-repo / release-repo）
     * @param gitToken  待验证的 git 令牌（CNB 用户名固定 cnb）
     * @return true=令牌可写访问仓库；false=任何一步失败（含令牌无效/无写权限/网络异常）
     */
    public boolean testWriteAccess(String repoUrl, String localName, String gitToken) {
        if (gitToken == null || gitToken.isBlank()) return false;
        String probeFile = ".token-probe-" + UUID.randomUUID().toString().replace("-", "") + ".txt";
        try (Git git = openOrPull(repoUrl, localName)) {
            Path repo = repoDir(localName);
            Path probe = repo.resolve(probeFile);
            Files.writeString(probe, "token probe " + Instant.now());
            commitAndPush(git, gitToken, "token-probe: add " + probeFile);
            Files.deleteIfExists(probe);
            commitAndPush(git, gitToken, "token-probe: remove " + probeFile);
            return true;
        } catch (Exception e) {
            log.warn("[PluginMarket] 令牌探测失败（仓库={}）: {}", repoUrl, e.getMessage());
            return false;
        }
    }
}
