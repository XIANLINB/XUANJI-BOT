package XuanJi.llm.profile;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.pipeline.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户画像采集阶段 —— order=45（rate-limit 之后、pre-process 之前）。
 *
 * <p>对所有通过安全检查的群消息做画像统计（不 @ 也统计，全量消息模式），
 * 满足条件时由 {@link UserProfileService} 异步触发 LLM 画像提炼。
 * 本阶段不中断链路，必须 {@code chain.proceed()} 放行后续阶段。
 */
@Slf4j
@Component
public class UserProfileStage implements PipelineStage {

    private final UserProfileService profileService;

    public UserProfileStage(UserProfileService profileService) {
        this.profileService = profileService;
    }

    @Override public String name() { return "user-profile"; }
    @Override public int order() { return 45; }

    @Override
    public Result handle(XuanJiEvent event, PipelineChain chain) {
        try {
            if (event.isGroupEvent() && event.sender() != null) {
                String role = null;
                try {
                    if (event.platformData() != null) {
                        var node = event.platformData().path("author").path("member_role");
                        if (!node.isMissingNode() && !node.isNull()) {
                            role = node.asText();
                        }
                    }
                } catch (Exception ignored) {
                    // 读不到角色不影响画像统计
                }
                profileService.onGroupMessage(
                        event.bot() != null ? event.bot().selfId() : "",
                        event.group().groupId(),
                        event.sender().id(),
                        event.sender().nickname(),
                        role,
                        event.message() != null ? event.message().plainText() : "");
            }
        } catch (Exception e) {
            log.debug("[PROFILE] 统计跳过: {}", e.getMessage());
        }
        return chain.proceed();
    }
}
