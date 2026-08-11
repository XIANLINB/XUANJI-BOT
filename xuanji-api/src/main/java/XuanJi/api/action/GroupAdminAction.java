package XuanJi.api.action;

import XuanJi.api.event.XuanJiGroup;
import XuanJi.api.event.XuanJiUser;

import java.time.Duration;

/**
 * 群管动作抽象 — 屏蔽各平台群管理 API 的差异。
 *
 * <p>适配器声明支持的能力位（如 QQ 支持 setAdmin，OneBot 全支持）；
 * 不支持的调用返回 {@link UnsupportedOperationException}。
 */
public interface GroupAdminAction {

    /** 禁言 */
    void mute(XuanJiGroup group, XuanJiUser target, Duration duration);

    /** 踢出 */
    void kick(XuanJiGroup group, XuanJiUser target);

    /** 设置群名片 */
    void setCard(XuanJiGroup group, XuanJiUser target, String card);

    /** 设置/取消管理员 */
    void setAdmin(XuanJiGroup group, XuanJiUser target, boolean enable);
}
