package XuanJi.llm.chat;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.llm.LlmAvailability;
import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import org.springframework.stereotype.Component;

/**
 * LLM 兜底接管判定 —— 供平台 handler 决定「@机器人 未命中命令时是否由 LLM 接管」。
 *
 * <p>判定与 {@link LlmChatStage} 一致（enabled + 群白名单 + @ + 冷却），
 * 不包含命令命中判断（调用时机本身就在命令未命中之后）。
 */
@Component
public class LlmAvailabilityImpl implements LlmAvailability {

    private final LlmConfigStore configStore;
    private final LlmChatGuard guard;

    public LlmAvailabilityImpl(LlmConfigStore configStore, LlmChatGuard guard) {
        this.configStore = configStore;
        this.guard = guard;
    }

    @Override
    public boolean available(XuanJiEvent event) {
        if (event == null || event.message() == null) {
            return false;
        }
        LlmConfig cfg = configStore.get();
        String botKey = event.bot() != null ? event.bot().selfId() : "";
        if (event.isGroupEvent()) {
            if (!cfg.isEnabled()) return false;
            if (!groupAllowed(cfg, event.group().groupId())) return false;
            if (cfg.isMentionRequired() && !LlmMentionUtil.isAtBot(event)) return false;
            return !guard.isCooledDown(botKey, event.group().groupId(), cfg.getCooldownSeconds());
        }
        // C2C 单聊
        if (event.sender() == null) return false;
        String userId = event.sender().id();
        if (!cfg.isC2cEnabled()) return false;
        if (!c2cAllowed(cfg, userId)) return false;
        return !guard.isCooledDown(botKey, userId, cfg.getC2cCooldownSeconds());
    }

    static boolean groupAllowed(LlmConfig cfg, String groupId) {
        return cfg.getGroupIds() == null || cfg.getGroupIds().isEmpty()
                || cfg.getGroupIds().contains(groupId);
    }

    /** C2C 单聊白名单判定：空 = 全部用户可触发。 */
    static boolean c2cAllowed(LlmConfig cfg, String userId) {
        return cfg.getC2cUserIds() == null || cfg.getC2cUserIds().isEmpty()
                || cfg.getC2cUserIds().contains(userId);
    }
}
