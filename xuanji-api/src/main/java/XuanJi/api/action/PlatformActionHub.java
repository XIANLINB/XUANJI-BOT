package XuanJi.api.action;

import java.util.List;
import java.util.Map;

/**
 * 平台统一动作分发中枢 — 框架唯一的「动作协议」入口。
 *
 * <p>屏蔽平台差异：调用方只传 {@code botKey + 动作名 + 参数}，由中枢路由到
 * 对应平台适配器注册的处理器。无论 qqbot / onebot / 未来新平台，
 * 群信息、群状态、禁言、审批、撤回等能力在框架里都是统一动作。
 *
 * <p>协议：{@code dispatch(botKey, action, params) → {"ok": bool, "data": … | "error": "…"}}。
 */
public interface PlatformActionHub {

    /**
     * 执行统一动作。
     *
     * @param botKey 机器人标识（空串回退第一个已注册平台）
     * @param action 动作名（{@link PlatformActions}）
     * @param params 动作参数
     * @return 统一结果：{@code {"ok": true, "data": …}} 或 {@code {"ok": false, "error": "…"}}
     */
    Map<String, Object> dispatch(String botKey, String action, Map<String, Object> params);

    /** 列出某平台支持的动作名（botKey 空串 → 第一个已注册平台）。 */
    List<String> listActions(String botKey);

    /** 注册平台动作提供者（适配器启动时调用）。 */
    void register(PlatformActionProvider provider);
}
