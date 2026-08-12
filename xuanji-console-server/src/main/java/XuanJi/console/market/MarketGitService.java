package XuanJi.console.market;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 插件市场 git 仓库操作（JGit）— clone / pull / 提交 / push。
 *
 * <p>本地工作副本位于 {@code data/plugin-market/repo/}（运行时生成）。
 * 纯 Java 实现（JGit），发布包无需安装系统 git。
 *
 * <p>读操作（clone/pull）不需要凭据（公开仓库）；写操作（commit/push）需要配置管理员 git 凭据。
 */
@Slf4j
@Component
public class MarketGitService {

    /** 远程默认分支（CNB 仓库默认 main；clone 时若默认分支不同会回退到自动检出）。 */
    private static final String BRANCH = "main";

    /** 本地工作副本目录（相对工作目录，与 data/ 同级）。 */
    public Path repoDir() {
        return Paths.get("data", "plugin-market", "repo");
    }

    /**
     * 打开本地工作副本并同步到最新（不存在则 clone）。
     *
     * @return 打开的 Git 对象（调用方负责 close）
     */
    public Git openOrPull(MarketSettings s) throws Exception {
        File dir = repoDir().toFile();
        UsernamePasswordCredentialsProvider creds = credentials(s);
        if (dir.exists() && new File(dir, ".git").exists()) {
            Git git = Git.open(dir);
            try {
                git.pull().setRemote("origin")
                        .setRemoteBranchName(BRANCH)
                        .setCredentialsProvider(creds)
                        .call();
            } catch (Exception e) {
                log.warn("[PluginMarket] pull 失败（继续使用本地副本）: {}", e.getMessage());
            }
            return git;
        }
        Files.createDirectories(repoDir());
        log.info("[PluginMarket] 克隆市场仓库: {}", s.getRepoUrl());
        try {
            return Git.cloneRepository().setURI(s.getRepoUrl())
                    .setDirectory(dir)
                    .setBranch(BRANCH)
                    .setCredentialsProvider(creds)
                    .call();
        } catch (Exception e) {
            // 默认分支非 main 时回退：不指定分支 clone
            log.warn("[PluginMarket] clone(main) 失败，尝试默认分支: {}", e.getMessage());
            return Git.cloneRepository().setURI(s.getRepoUrl())
                    .setDirectory(dir)
                    .setCredentialsProvider(creds)
                    .call();
        }
    }

    /** add 全部 + commit + push（写操作，必须已配置凭据）。 */
    public void commitAndPush(Git git, MarketSettings s, String message) throws Exception {
        if (!s.hasCredential()) {
            throw new IllegalStateException("插件市场写操作需要配置 git 凭据（设置页填写 git_user / git_token）");
        }
        git.add().addFilepattern(".").call();
        git.commit().setMessage(message)
                .setAuthor(s.getGitUser(), s.getGitUser() + "@noreply.cnb.cool")
                .call();
        git.push().setRemote("origin")
                .setCredentialsProvider(credentials(s))
                .call();
        log.info("[PluginMarket] 已推送: {}", message);
    }

    private static UsernamePasswordCredentialsProvider credentials(MarketSettings s) {
        return s.hasCredential()
                ? new UsernamePasswordCredentialsProvider(s.getGitUser(), s.getGitToken())
                : null;
    }
}
