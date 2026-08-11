package XuanJi.starter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 机器人配置文件（xuanji-robots.yml）的集中定位。
 *
 * <p>统一使用运行时目录 {@code data/xuanji-robots.yml}（相对工作目录），而非源码目录。
 * 这样 jar 部署下，首启向导与 XuanJiBot 管理写入的配置，能被框架通过同一路径读取
 * （{@code XuanJiRobotProperties} 的 @PropertySource 也指向这里）。
 */
public final class RobotsFile {

    /** 运行时配置文件路径：{user.dir}/data/xuanji-robots.yml */
    public static final Path PATH = Paths.get("data", "xuanji-robots.yml");

    private RobotsFile() {}

    /** 确保父目录（data/）存在，写文件前调用 */
    public static void ensureDirs() throws Exception {
        Path parent = PATH.getParent();
        if (parent != null) Files.createDirectories(parent);
    }
}
