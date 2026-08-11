package XuanJi.core.storage;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 平台共享库 Schema SPI — 各适配器在平台共享库（data/{platform}/{platform}.mv.db）
 * 建 bot/botinfo 表（全 bot 一张表，id 全局唯一）。
 */
public interface BotSchemaProvider {

    /** 平台标识：qqbot / onebot。 */
    String platform();

    /** 初始化平台共享库结构（建 qqbot_bot / onebot_bot 等表）。 */
    void initPlatformSchema(JdbcTemplate platformJdbc);

    /**
     * 初始化 per-bot 实例库（数据）结构：group / group_member / user 3 张数据档案表。
     *
     * <p>消息/事件流水已拆分到 per-bot 日志库（见 {@link #initLogSchema}）。
     */
    void initSchema(JdbcTemplate instanceJdbc);

    /**
     * 初始化 per-bot 日志库结构：message / event 2 张流水表。
     *
     * <p>日志库路径 {@code data/{platform}/{instanceId}/log/{instanceId}.log.mv.db}，
     * 与业务数据分库自治；删 bot 时随实例目录一并归档删除。
     */
    void initLogSchema(JdbcTemplate logJdbc);
}
