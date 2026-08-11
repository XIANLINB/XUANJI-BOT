package XuanJi.scheduler.exec;

import XuanJi.core.storage.BotArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BOT_ARCHIVE_CLEANUP 执行器：删除已过期（TTL 30 天）的机器人归档，释放磁盘。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotArchiveCleanupExecutor implements JobExecutor {

    private final BotArchiveService botArchiveService;

    @Override
    public String type() {
        return "BOT_ARCHIVE_CLEANUP";
    }

    @Override
    public String execute(Map<String, Object> job) throws Exception {
        int removed = botArchiveService.deleteExpired();
        return "清理过期归档 " + removed + " 条";
    }
}
