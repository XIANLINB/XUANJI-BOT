package XuanJi.llm.proactive;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 主动搭话内置话术库 —— 零 token 的活跃气氛内容。
 *
 * <p>两类：
 * <ul>
 *   <li>ASK：@ 最近活跃成员问话（文本，内嵌 {@code <@openid>}）</li>
 *   <li>TOPIC：群聊话题卡片（markdown，主动抛话题 / 冷笑话 / 小知识）</li>
 * </ul>
 * 纯模板拼接，不调 LLM，冷场活跃零成本；后续可扩展「LLM 生成主动话术」开关。
 */
public final class ProactiveTemplates {

    private static final List<String> ASK_TEMPLATES = List.of(
            "<@%s> 在干嘛呢？群里安静得我都要长蘑菇了～",
            "<@%s> 这么安静，是都在认真上班学习吗？来聊两句呗",
            "<@%s> 突然好奇，你上次和我说的那件事后来怎么样了？",
            "<@%s> 捕捉一只潜水的%s！快出来冒个泡",
            "<@%s> 气氛这么冷，来点话题？你今天遇到什么有意思的事了吗"
    );

    private static final List<String> TOPIC_TEMPLATES = List.of(
            "**今日话题** 💬\n> 如果你有超能力，你最想用来做什么？",
            "**冷知识时间** 🧠\n> 你知道吗？蜜蜂是能分辨人类面孔的动物之一。",
            "**今日冷笑话** ❄️\n> 为什么程序员分不清万圣节和圣诞节？因为 Oct 31 == Dec 25。",
            "**周末预告** 🎉\n> 周末快到了，大家有什么计划？组团游戏还是出门浪？",
            "**今日提问** ❓\n> 推荐一部你最近看过的剧/电影/书吧！"
    );

    private ProactiveTemplates() {}

    /** 随机取一条 @ 问话模板（%s 依次为 userOpenid / 昵称）。 */
    public static String randomAsk(String userOpenid, String nickname) {
        String t = ASK_TEMPLATES.get(ThreadLocalRandom.current().nextInt(ASK_TEMPLATES.size()));
        return t.formatted(userOpenid, nickname == null ? "" : nickname);
    }

    /** 随机取一条话题卡片 markdown。 */
    public static String randomTopic() {
        return TOPIC_TEMPLATES.get(ThreadLocalRandom.current().nextInt(TOPIC_TEMPLATES.size()));
    }
}
