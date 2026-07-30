package dev.xuanji.api.action;

import dev.xuanji.api.event.XuanjiGroup;
import dev.xuanji.api.event.XuanjiUser;

import java.time.Duration;

/**
 * 群管动作抽象 — 屏蔽各平台群管理 API 的差异。
 *
 * <p>适配器声明支持的能力位（如 QQ 支持 setAdmin，OneBot 全支持）；
 * 不支持的调用返回 {@link UnsupportedOperationException}。
 */
public interface GroupAdminAction {

    /** 禁言 */
    void mute(XuanjiGroup group, XuanjiUser target, Duration duration);

    /** 踢出 */
    void kick(XuanjiGroup group, XuanjiUser target);

    /** 设置群名片 */
    void setCard(XuanjiGroup group, XuanjiUser target, String card);

    /** 设置/取消管理员 */
    void setAdmin(XuanjiGroup group, XuanjiUser target, boolean enable);
}
