package XuanJi.llm.proactive;

import XuanJi.api.event.XuanJiEvent;
import XuanJi.api.pipeline.PipelineStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 群活跃度采集阶段 —— order=44（全量消息：记录每群最后活跃时间/今日消息数/最近活跃成员）。
 *
 * <p>独立于 {@code profileEnabled} 开关：主动搭话的冷场判定不依赖用户画像。
 * 本阶段不中断链路，必须 {@code chain.proceed()}。
 */
@Slf4j
@Component
public class GroupActivityStage implements PipelineStage {

    private final GroupActivityTracker tracker;

    public GroupActivityStage(GroupActivityTracker tracker) {
        this.tracker = tracker;
    }

    @Override public String name() { return "group-activity"; }
    @Override public int order() { return 44; }

    @Override
    public Result handle(XuanJiEvent event, PipelineChain chain) {
        try {
            if (event.isGroupEvent() && event.sender() != null) {
                tracker.onMessage(
                        event.bot() != null ? event.bot().selfId() : "",
                        event.group().groupId(),
                        event.sender().id(),
                        event.sender().nickname());
            }
        } catch (Exception e) {
            log.debug("[ACTIVITY] 统计跳过: {}", e.getMessage());
        }
        return chain.proceed();
    }
}
