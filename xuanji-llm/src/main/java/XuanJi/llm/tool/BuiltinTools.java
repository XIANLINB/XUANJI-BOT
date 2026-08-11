package XuanJi.llm.tool;

import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.proactive.GroupActivityTracker;
import XuanJi.llm.proactive.ProactiveTemplates;
import XuanJi.llm.profile.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 内置 LLM 工具集 —— 演示 @LlmTool 能力，可被模型在对话中按需调用。
 *
 * <p>全部为只读/娱乐类（confirm=false 直接执行）。危险工具（发消息/建任务）由
 * 业务方标 {@code confirm=true}，经确认流程后才执行。
 */
@Slf4j
@Component
public class BuiltinTools {

    private final GroupActivityTracker tracker;
    private final UserProfileService profileService;

    public BuiltinTools(GroupActivityTracker tracker, UserProfileService profileService) {
        this.tracker = tracker;
        this.profileService = profileService;
    }

    @LlmTool(name = "get_time",
            descriptionZh = "获取当前日期和时间", description = "获取当前日期和时间。用于回答「现在几点」「今天几号」「星期几」等问题", confirm = false)
    public String getTime(@LlmToolParam(name = "timezone", value = "时区，如 Asia/Shanghai / UTC，可选，默认系统时区", required = false) String timezone) {
        ZoneId zone = parseZone(timezone);
        String now = LocalDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE HH:mm"));
        return "当前时间（" + zone + "）：" + now;
    }

    @LlmTool(name = "group_stats",
            descriptionZh = "查询当前群今日活跃度（消息条数）", description = "查询当前群今天的活跃度（今日消息条数）。用于回答「今天群里聊了多少条」等问题", confirm = false)
    public String groupStats(LlmToolContext ctx) {
        long count = tracker.todayMsgCount(ctx != null ? ctx.botKey() : "", ctx != null ? ctx.groupId() : "");
        return "当前群今日消息数：" + count + " 条";
    }

    @LlmTool(name = "who_am_i",
            descriptionZh = "查询当前对话者身份（昵称/角色/画像）", description = "查询当前正在和我说话的用户是谁（昵称、群角色、画像认知、说话风格）。用于认出对话者", confirm = false)
    public String whoAmI(LlmToolContext ctx) {
        if (ctx == null || ctx.userId() == null) return "暂无可识别身份信息";
        String prompt = profileService.buildProfilePrompt(
                ctx.botKey() == null ? "" : ctx.botKey(),
                ctx.groupId() == null ? "" : ctx.groupId(),
                ctx.userId());
        return prompt == null || prompt.isBlank() ? "当前对话者暂未建档（需开启用户画像并累积消息）" : prompt;
    }

    @LlmTool(name = "today_topic",
            descriptionZh = "获取今日话题或冷知识，活跃气氛", description = "获取一条今日话题/冷知识/冷笑话，用于群冷场时活跃气氛", confirm = false)
    public String todayTopic() {
        return ProactiveTemplates.randomTopic().replace("**", "").replace("> ", "");
    }

    @LlmTool(name = "weather",
            descriptionZh = "查询城市天气", description = "查询城市当前天气（免费接口 wttr.in）。用于回答「今天天气怎么样」", confirm = false)
    public String weather(@LlmToolParam(name = "city", value = "城市名，如 北京 / Shanghai，必填") String city) {
        if (city == null || city.isBlank()) return "请提供城市名";
        try {
            String encoded = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
            RestClient client = RestClient.builder()
                    .baseUrl("https://wttr.in")
                    .requestFactory(jdkFactory())
                    .build();
            String text = client.get()
                    .uri("/{city}?format=%C+%t+%w", encoded)
                    .retrieve()
                    .body(String.class);
            return text == null || text.isBlank() ? "查询不到 " + city + " 的天气" : city + "：" + text.trim();
        } catch (Exception e) {
            return "天气查询失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    @LlmTool(name = "roll_dice",
            descriptionZh = "掷 1-100 随机数（抽签/做决定）", description = "掷一个 1-100 的随机数，用于抽签/做决定/玩游戏", confirm = false)
    public String rollDice() {
        return "你掷出了 **" + ThreadLocalRandom.current().nextInt(1, 101) + "**";
    }

    private static ZoneId parseZone(String timezone) {
        try {
            if (timezone != null && !timezone.isBlank()) {
                return ZoneId.of(timezone.trim());
            }
        } catch (Exception ignored) {
        }
        return ZoneOffset.systemDefault();
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(10));
        return f;
    }
}
