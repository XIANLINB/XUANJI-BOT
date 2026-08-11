package XuanJi.api.action;

import java.util.Map;

/**
 * 平台统一动作处理器 — 适配器为某个动作注册的实现。
 *
 * <p>返回约定：{@code {"ok": true, "data": …}} 成功；{@code {"ok": false, "error": "…"}} 失败。
 * 由适配器在自身上下文内执行（框架分发时已绑定 botKey）。
 */
@FunctionalInterface
public interface PlatformActionHandler {

    /** 执行动作。 */
    Map<String, Object> handle(Map<String, Object> params);
}
