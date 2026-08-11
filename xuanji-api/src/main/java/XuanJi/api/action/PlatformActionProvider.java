package XuanJi.api.action;

import java.util.Map;

/**
 * 平台动作提供者 — 适配器向 {@link PlatformActionHub} 声明自己支持的动作集。
 *
 * <p>接入新平台 / 平台新增能力：适配器实现本接口并注册动作表即可，
 * 框架（动作名协议）与插件（调用方）无需改动。
 */
public interface PlatformActionProvider {

    /** 平台标识（如 {@code "qq"} / {@code "onebot"}）。 */
    String platform();

    /**
     * 判断 botKey 是否属于本平台。
     *
     * <p>qqbot 的 botKey 是纯 AppID（无前缀）；onebot 是 {@code onebot:{selfId}} 格式。
     * 分发时先按 {@code 前缀:} 精确匹配，再用本方法兜底。
     */
    boolean matches(String botKey);

    /** 平台动作表：动作名（{@link PlatformActions}）→ 处理器。 */
    Map<String, PlatformActionHandler> actions();
}
