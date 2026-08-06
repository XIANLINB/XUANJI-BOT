package dev.xuanji.core.storage;

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

    /** 初始化 per-bot 实例库结构（group / group_member / user / message / event 5 张业务表）。 */
    void initSchema(JdbcTemplate instanceJdbc);
}
