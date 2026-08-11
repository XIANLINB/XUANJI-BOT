package XuanJi.scheduler.exec;

import XuanJi.core.sync.GroupStateSyncer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * BOT_GROUP_STATE_SYNC 执行器：机器人在群内状态定时错峰同步。
 *
 * <p>每轮最多处理 {@link #BATCH_LIMIT} 个（30 QPM 预算的 2/3 安全线，留余量给事件/手动），
 * 按"最久未同步"排序，由 {@link GroupStateSyncer#syncDue} 内做间隔/冷却判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotGroupStateSyncExecutor implements JobExecutor {

    /** 每轮最多同步数（30 QPM 的 2/3）。 */
    private static final int BATCH_LIMIT = 20;

    private final ObjectProvider<GroupStateSyncer> syncerProvider;

    @Override
    public String type() {
        return "BOT_GROUP_STATE_SYNC";
    }

    @Override
    public String execute(Map<String, Object> job) throws Exception {
        GroupStateSyncer syncer = syncerProvider.getIfAvailable();
        if (syncer == null) {
            return "无群状态同步器（未接入 qqbot 适配器）";
        }
        int done = syncer.syncDue(BATCH_LIMIT);
        return "机器人群内状态错峰同步 " + done + " 个";
    }
}
