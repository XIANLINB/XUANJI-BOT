package dev.xuanji.api.capability;

import dev.xuanji.api.event.XuanjiUser;

/**
 * 经济服务接口 — 跨插件共享的货币系统。
 *
 * <p>��� xuanji-plugin-economy 实现；签到插件调 earn，银行插件调 balance/transfer，
 * 两边零耦合，金币天然一致。内置 reason 记账流水可对账回滚。
 */
public interface EconomyService {

    /** 赚取 */
    long earn(XuanjiUser user, long amount, String reason);

    /** 查询余额 */
    long balance(XuanjiUser user);

    /** 转账 */
    boolean transfer(XuanjiUser from, XuanjiUser to, long amount, String reason);

    /** 消耗（扣款，余额不足时返回 false） */
    boolean spend(XuanjiUser user, long amount, String reason);
}
