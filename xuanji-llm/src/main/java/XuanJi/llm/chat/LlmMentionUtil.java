package XuanJi.llm.chat;

import XuanJi.api.event.XuanJiEvent;
import tools.jackson.databind.JsonNode;

/**
 * LLM 群聊 @机器人 判定 —— 兼容 QQ 平台两种订阅模式（平台无关，读 platformData）。
 *
 * <ul>
 *   <li><b>AT 消息模式</b> {@code GROUP_AT_MESSAGE_CREATE}：官方仅在下发 @机器人 消息，
 *       且事件不携带 mentions → 事件类型本身即判定依据；</li>
 *   <li><b>全量消息模式</b> {@code GROUP_MESSAGE_CREATE}：靠 {@code mentions[].is_you} 判定
 *       （@了机器人 才为 true；只 @他人 不触发）。</li>
 * </ul>
 *
 * <p>不依赖 {@code XuanJiMessage.isAtBot()}（后者语义是「链内含任意 @ 提及」，@他人 也会返回 true，
 * 会误触发 LLM 闲聊）。
 */
public final class LlmMentionUtil {

    private LlmMentionUtil() {}

    public static boolean isAtBot(XuanJiEvent event) {
        if (event == null || event.message() == null) {
            return false;
        }
        JsonNode pd = event.platformData();
        if (pd == null) {
            return false;
        }
        // AT 订阅模式：事件本身即 @机器人（无 mentions 字段）
        String eventType = pd.path("_eventType").asText("");
        if ("GROUP_AT_MESSAGE_CREATE".equals(eventType)) {
            return true;
        }
        // 全量消息模式：@机器人 时 mentions 含 is_you=true
        JsonNode mentions = pd.path("mentions");
        if (mentions.isArray()) {
            for (JsonNode m : mentions) {
                if (m.path("is_you").asBoolean(false)) {
                    return true;
                }
            }
        }
        return false;
    }
}
