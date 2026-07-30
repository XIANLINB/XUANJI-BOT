package dev.xuanji.core.event.handler.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.xuanji.core.config.XuanjiRobotProperties;
import dev.xuanji.api.dto.GroupMessageEvent;
import dev.xuanji.adapter.qq.api.MessageSender;
import dev.xuanji.core.event.EventHandler;
import dev.xuanji.core.event.EventMapping;
import dev.xuanji.adapter.qq.util.KeyboardBuilder;
import dev.xuanji.adapter.qq.util.MarkdownBuilder;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.xuanji.api.json.Json;
import org.springframework.stereotype.Component;

/**
 * 群聊消息事件处理器
 *
 * <h3>测试命令（@机器人后发送）</h3>
 * <ul>
 *   <li>文本 — 文本消息</li>
 *   <li>markdown — Markdown 消息</li>
 *   <li>按钮 — Markdown + 键盘按钮</li>
 *   <li>ark23 — Ark 链接+文本列表模板</li>
 *   <li>ark24 — Ark 文本+缩略图模板</li>
 *   <li>ark37 — Ark 大图模板</li>
 *   <li>图片 — 图片消息</li>
 *   <li>语音 — 语音消息</li>
 *   <li>视频 — 视频消息</li>
 * </ul>
 */
@Slf4j
@Component
@EventMapping({"GROUP_MESSAGE_CREATE", "GROUP_AT_MESSAGE_CREATE"})
public class GroupMessageHandler implements EventHandler {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final XuanjiRobotProperties robotProperties;
    private final MessageSender messageSender;

    public GroupMessageHandler(XuanjiRobotProperties robotProperties, MessageSender messageSender) {
        this.robotProperties = robotProperties;
        this.messageSender = messageSender;
    }

    @Override
    public String getEventType() {
        return "GROUP_MESSAGE_EVENT";
    }

    @Override
    public void handle(Long robotId, String envType, ObjectNode data) {
        try {
            GroupMessageEvent event = objectMapper.readValue(data.toString(), GroupMessageEvent.class);

            // 检查是否为机器人发送的消息
            if (event.getAuthor() != null && Boolean.TRUE.equals(event.getAuthor().getBot())) {
                if (robotProperties.isIgnoreBotMessages()) {
                    return;
                }
            }

            String content = event.getPlainTextContent().trim();
            String msgId = event.getId();
            String groupOpenid = event.getGroupOpenid();

            log.info("[收到群聊消息][群{}] sender={}, content={}",
                    groupOpenid, event.getAuthor().getUsername(), content);

            // 根据命令回复不同类型的消息
            switch (content) {
                case "文本" -> testText(groupOpenid, msgId);
                case "markdown" -> testMarkdown(groupOpenid, msgId);
                case "按钮" -> testMarkdownWithKeyboard(groupOpenid, msgId);
                case "ark23" -> testArk23(groupOpenid, msgId);
                case "ark24" -> testArk24(groupOpenid, msgId);
                case "ark37" -> testArk37(groupOpenid, msgId);
                case "图片" -> testImage(groupOpenid, msgId);
                case "语音" -> testAudio(groupOpenid, msgId);
                case "视频" -> testVideo(groupOpenid, msgId);
                default -> {
                    if (event.isAtBot()) {
                        messageSender.sendGroupText(groupOpenid,
                                "可用命令：\n文本 | markdown | 按钮\nark23 | ark24 | ark37\n图片 | 语音 | 视频", msgId);
                    }
                }
            }

        } catch (Exception e) {
            log.error("[群聊消息] 解析异常: robotId={}, error={}", robotId, e.getMessage(), e);
        }
    }

    /** 测试文本消息 */
    private void testText(String groupOpenid, String msgId) {
        messageSender.sendGroupText(groupOpenid,
                "这是一条文本消息\n时间: " + java.time.LocalDateTime.now(), msgId);
    }

    /** 测试 Markdown 消息 */
    private void testMarkdown(String groupOpenid, String msgId) {
        String md = MarkdownBuilder.create()
                .h1("Markdown 测试")
                .quote("这是引用1")
                .text("普通文本")
                .bold("加粗", "这是加粗内容")
                .quote("这是引用2")
                .divider()
                .link("QQ 机器人文档", "https://bot.q.qq.com")
                .build();

        messageSender.sendGroupMarkdown(groupOpenid, Json.parseObj(md), null, msgId);
    }

    /** 测试 Markdown + 键盘按钮 */
    private void testMarkdownWithKeyboard(String groupOpenid, String msgId) {
        String md = MarkdownBuilder.create()
                .h2("功能菜单")
                .text("请选择功能：")
                .build();

        String kb = KeyboardBuilder.create()
                .addButton("sign_in", "签到", "签到")
                .addButton("bank", "银行", "银行")
                .addButton("help", "帮助", "帮助")
                .build();

        messageSender.sendGroupMarkdown(groupOpenid, Json.parseObj(md), Json.parseObj(kb), msgId);
    }

    /**
     * 测试 Ark 模板 23 — 链接+文本列表
     */
    private void testArk23(String groupOpenid, String msgId) {
        ObjectNode ark = Json.obj();
        ark.put("template_id", 23);
        ArrayNode kv = Json.arr();
        kv.add(Json.obj().put("key", "#LIST#").put("value", "功能1|功能2|功能3"));
        kv.add(Json.obj().put("key", "#DESC#").put("value", "璇玑机器人功能列表"));
        kv.add(Json.obj().put("key", "#PROMPT#").put("value", "请选择功能"));
        kv.add(Json.obj().put("key", "#TITLE#").put("value", "璇玑机器人"));
        kv.add(Json.obj().put("key", "#LINK#").put("value", "https://bot.q.qq.com"));
        kv.add(Json.obj().put("key", "#METATITLE#").put("value", "官方文档"));
        kv.add(Json.obj().put("key", "#METADESC#").put("value", "QQ 机器人开放平台"));
        kv.add(Json.obj().put("key", "#METAICON#").put("value", "https://q.qlogo.cn/g?b=qq&nk=10001&s=100"));
        ark.put("kv", kv);

        messageSender.sendGroupArk(groupOpenid, ark, msgId);
    }

    /**
     * 测试 Ark 模板 24 — 文本+缩略图
     */
    private void testArk24(String groupOpenid, String msgId) {
        ObjectNode ark = Json.obj();
        ark.put("template_id", 24);
        ArrayNode kv = Json.arr();
        kv.add(Json.obj().put("key", "#DESC#").put("value", "这是一条 Ark 测试消息"));
        kv.add(Json.obj().put("key", "#PROMPT#").put("value", "Ark 模板24"));
        kv.add(Json.obj().put("key", "#TITLE#").put("value", "文本+缩略图模板"));
        kv.add(Json.obj().put("key", "#METADESC#").put("value", "支持多种 Ark 模板消息"));
        kv.add(Json.obj().put("key", "#IMG#").put("value", "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg"));
        kv.add(Json.obj().put("key", "#LINK#").put("value", "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg"));
        kv.add(Json.obj().put("key", "#SUBTITLE#").put("value", "璇玑机器人"));
        ark.put("kv", kv);

        messageSender.sendGroupArk(groupOpenid, ark, msgId);
    }

    /**
     * 测试 Ark 模板 37 — 大图模板
     */
    private void testArk37(String groupOpenid, String msgId) {
        ObjectNode ark = Json.obj();
        ark.put("template_id", 37);
        ArrayNode kv = Json.arr();
        kv.add(Json.obj().put("key", "#PROMPT#").put("value", "Ark 模板37"));
        kv.add(Json.obj().put("key", "#METATITLE#").put("value", "豆包机器人"));
        kv.add(Json.obj().put("key", "#METASUBTITLE#").put("value", "非常好用"));
        kv.add(Json.obj().put("key", "#METACOVER#").put("value", "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg"));
        kv.add(Json.obj().put("key", "#METAURL#").put("value", "https://bot.q.qq.com"));
        ark.put("kv", kv);

        messageSender.sendGroupArk(groupOpenid, ark, msgId);
    }

    /**
     * 测试图片消息
     */
    private void testImage(String groupOpenid, String msgId) {
        messageSender.sendGroupImage(groupOpenid,
                "https://c-ssl.duitang.com/uploads/blog/202605/05/3BS4exw0czd3Vlg.jpg", msgId);
    }

    /**
     * 测试语音消息
     */
    private void testAudio(String groupOpenid, String msgId) {
        messageSender.sendGroupAudio(groupOpenid,
                "http://music.163.com/song/media/outer/url?id=862101001.mp3", msgId);
    }

    /**
     * 测试视频消息
     */
    private void testVideo(String groupOpenid, String msgId) {
        messageSender.sendGroupVideo(groupOpenid,
                "https://alimov2.a.kwimgs.com/upic/2023/05/29/12/BMjAyMzA1MjkxMjA4MzBfODc4ODYzMTE3XzEwNDI0ODk2ODEwMl8xXzM=_b_Bbb1823a5e9bee463527153837d84de6d.mp4?clientCacheKey=3xnssi587b5nx56_b.mp4&tt=b&di=78e49c3f&bp=13414",
                msgId);
    }
}
