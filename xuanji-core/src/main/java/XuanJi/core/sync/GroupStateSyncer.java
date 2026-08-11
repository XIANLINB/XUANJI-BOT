package XuanJi.core.sync;

/**
 * 机器人群内状态同步 SPI — 定时错峰刷新并持久化 {@code bot_state}（30 QPM 预算内）。
 *
 * <p>由平台适配器实现（qqbot 的 {@code GroupRobotStateSyncService}），
 * scheduler 系统任务（{@code BOT_GROUP_STATE_SYNC}）每周期调用 {@link #syncDue}，
 * 消费方（撤回/禁言等权限判断）读持久化角色，不实时调接口。
 */
public interface GroupStateSyncer {

    /** 立即同步指定机器人在指定群的状态；返回是否成功。 */
    boolean sync(String appId, String groupOpenid);

    /** 错峰同步到期待处理的 (bot, group) 对，每轮最多 limit 个；返回成功同步条数。 */
    int syncDue(int limit);
}
