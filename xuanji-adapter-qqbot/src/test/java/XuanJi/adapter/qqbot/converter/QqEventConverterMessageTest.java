package XuanJi.adapter.qqbot.converter;

import tools.jackson.databind.node.ObjectNode;
import XuanJi.api.json.Json;
import XuanJi.api.message.XuanJiMessage;
import XuanJi.api.message.XuanJiMessageElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QQ 入站消息链解析回归 — 锁死「管道路径丢富媒体」这个历史 bug。
 *
 * <p>背景：框架有两条并行的入站路径 ——
 * <ol>
 *   <li>{@code QqEventConverter.convert} → {@code XuanJiEvent} → 命令路由 / 权限 / 黑名单</li>
 *   <li>{@code GroupMessageHandler.sdkEvent} → SDK 事件 → 插件</li>
 * </ol>
 * 修复前路径 ① 的 {@code extractMessage} 只读 {@code content} 纯文本，
 * {@code attachments} 直接丢弃、{@code mentions} 是空转循环，于是出现
 * 「插件收得到图片，命令匹配却只看得见空文本」的诡异分叉。
 * 现在两条路径共用 {@link QqMessageConverter#fromQqData}，本测试即为分叉的护栏。
 *
 * <p>注：所有用例 appId 传 {@code null} —— 关闭媒体自动下载，媒体保持 URL 形态，
 * 测试不触网、不落盘。
 */
@DisplayName("QqEventConverter.extractMessage 入站解析")
class QqEventConverterMessageTest {

    private static ObjectNode data(String json) {
        return Json.parseObj(json);
    }

    // ==================== 核心回归：富媒体不再丢失 ====================

    @Test
    @DisplayName("群 @ 消息带图片 → At + Text + Image 齐全（修复前只剩 Text）")
    void groupAtMessageWithImageKeepsEverything() {
        // 真实 GROUP_AT_MESSAGE_CREATE 形态：QQ 把 @机器人 剥离到 mentions，content 只剩正文
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "id": "MSGID_1",
                  "content": " 天气 北京",
                  "group_openid": "GROUP_A",
                  "author": {"id": "AUTHOR_1", "member_openid": "MEMBER_1"},
                  "mentions": [{"id": "BOT_1", "member_openid": "BOT_MEMBER_1", "username": "小玄"}],
                  "attachments": [
                    {"url": "https://example.com/a.png", "filename": "aovyw5.png", "size": 119364}
                  ]
                }"""), "GROUP_AT_MESSAGE_CREATE", null);

        assertNotNull(chain);
        assertTrue(chain.has(XuanJiMessageElement.Image.class), "attachments 里的图片不该被丢掉");
        assertEquals(" 天气 北京", chain.plainText(), "正文应原样保留（含前导空格，命令解析器自己 trim）");
        assertTrue(chain.isAtBot(), "mentions 应还原成 At，否则 isAtBot() 在管道侧永远 false");

        // 语序：At 在正文之前 —— 还原「@机器人 天气 北京」的真实输入
        assertInstanceOf(XuanJiMessageElement.At.class, chain.elements().get(0));
        assertEquals("[@][图片]", chain.summary());
    }

    @Test
    @DisplayName("纯附件消息（content 为空）→ 不返回 null，媒体元素照样在")
    void attachmentOnlyMessageIsNotNull() {
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "id": "MSGID_2",
                  "content": "",
                  "attachments": [
                    {"url": "https://example.com/v.mp4", "filename": "clip.mp4", "size": 88}
                  ]
                }"""), "GROUP_MESSAGE_CREATE", null);

        assertNotNull(chain, "只发一个视频也是有效消息，不能当空消息吞掉");
        assertTrue(chain.hasMedia());
        assertTrue(chain.has(XuanJiMessageElement.Video.class));
    }

    // ==================== 事件层字段：Reply / At ====================

    @Test
    @DisplayName("message_reference → Reply 且置于链首")
    void messageReferenceBecomesReplyAtHead() {
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "id": "MSGID_3",
                  "content": "收到",
                  "message_reference": {"message_id": "SRC_MSG_9"}
                }"""), "C2C_MESSAGE_CREATE", null);

        assertNotNull(chain);
        XuanJiMessageElement head = chain.elements().get(0);
        assertInstanceOf(XuanJiMessageElement.Reply.class, head);
        assertEquals("SRC_MSG_9", ((XuanJiMessageElement.Reply) head).targetMsgId());
        assertEquals("收到", chain.plainText());
    }

    @Test
    @DisplayName("Reply 与 At 同时存在 → Reply 在前、At 在后、正文垫底")
    void replyThenAtThenBody() {
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "content": "再说一遍",
                  "message_reference": {"message_id": "SRC_MSG_10"},
                  "mentions": [{"member_openid": "MEMBER_X", "username": "阿玄"}]
                }"""), "GROUP_AT_MESSAGE_CREATE", null);

        assertNotNull(chain);
        List<XuanJiMessageElement> els = chain.elements();
        assertInstanceOf(XuanJiMessageElement.Reply.class, els.get(0));
        assertInstanceOf(XuanJiMessageElement.At.class, els.get(1));
        assertInstanceOf(XuanJiMessageElement.Text.class, els.get(2));
    }

    @Test
    @DisplayName("mentions 取 ID 优先 member_openid（与 extractUser 口径一致）")
    void mentionPrefersMemberOpenid() {
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "content": "hi",
                  "mentions": [
                    {"id": "GLOBAL_ID", "member_openid": "MEMBER_ID", "username": "甲"},
                    {"id": "ONLY_GLOBAL_ID", "username": "乙"}
                  ]
                }"""), "GROUP_AT_MESSAGE_CREATE", null);

        assertNotNull(chain);
        List<XuanJiMessageElement.At> ats = chain.elements().stream()
                .filter(XuanJiMessageElement.At.class::isInstance)
                .map(XuanJiMessageElement.At.class::cast)
                .toList();

        assertEquals(2, ats.size());
        assertEquals("MEMBER_ID", ats.get(0).userId(), "群场景应取 member_openid，便于与 sender.userId 直接比对");
        assertEquals("甲", ats.get(0).display());
        assertEquals("ONLY_GLOBAL_ID", ats.get(1).userId(), "没有 member_openid 时回退到 id");
    }

    @Test
    @DisplayName("mentions 元素缺任何可用 ID → 跳过，不产出空 At")
    void mentionWithoutAnyIdIsSkipped() {
        XuanJiMessage chain = QqEventConverter.extractMessage(data("""
                {
                  "content": "hi",
                  "mentions": [{"username": "无名氏"}]
                }"""), "GROUP_AT_MESSAGE_CREATE", null);

        assertNotNull(chain);
        assertFalse(chain.isAtBot(), "没有 ID 的 mention 不应变成 At，否则会污染 isAtBot 判定");
        assertEquals("hi", chain.plainText());
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("纯文本消息 → 只有 Text，不夹带任何前缀元素")
    void plainTextHasNoPrefix() {
        XuanJiMessage chain = QqEventConverter.extractMessage(
                data("{\"id\":\"MSGID_4\",\"content\":\"/help\"}"), "C2C_MESSAGE_CREATE", null);

        assertNotNull(chain);
        assertEquals(1, chain.elements().size());
        assertEquals("/help", chain.plainText());
        assertEquals("", chain.summary(), "不该凭空多出 @ 或媒体");
    }

    @Test
    @DisplayName("无任何内容 → 返回 null（与 convert 的空消息语义对齐）")
    void emptyPayloadReturnsNull() {
        assertNull(QqEventConverter.extractMessage(
                data("{\"id\":\"MSGID_5\",\"content\":\"\"}"), "GROUP_MESSAGE_CREATE", null));
        assertNull(QqEventConverter.extractMessage(
                data("{}"), "GROUP_MESSAGE_CREATE", null));
    }

    @Test
    @DisplayName("mentions 不是数组 → 安全忽略，不抛异常")
    void malformedMentionsDoesNotThrow() {
        XuanJiMessage chain = assertDoesNotThrow(() -> QqEventConverter.extractMessage(data("""
                {"content":"hi","mentions":"这里本该是数组","message_reference":"也不是对象"}"""),
                "GROUP_AT_MESSAGE_CREATE", null));

        assertNotNull(chain);
        assertEquals("hi", chain.plainText());
        assertFalse(chain.isAtBot());
    }

    // ==================== 双路径一致性护栏 ====================

    @Test
    @DisplayName("管道路径与 SDK 路径解析出的正文/媒体必须一致")
    void pipelineAndSdkPathAgreeOnBody() {
        String raw = """
                {
                  "content": "看图",
                  "attachments": [
                    {"url": "https://example.com/a.png", "filename": "a.png", "size": 1},
                    {"url": "https://example.com/b.pdf", "filename": "b.pdf", "size": 2}
                  ]
                }""";

        // 路径 ①：XuanJiEvent 管道
        XuanJiMessage pipeline = QqEventConverter.extractMessage(data(raw), "GROUP_MESSAGE_CREATE", null);
        // 路径 ②：SDK 事件（GroupMessageHandler.sdkEvent 用的就是这个入口）
        XuanJiMessage sdk = QqMessageConverter.fromQqData(data(raw), null);

        assertNotNull(pipeline);
        assertEquals(sdk.plainText(), pipeline.plainText());
        assertEquals(sdk.summary(), pipeline.summary());
        assertEquals(sdk.medias().size(), pipeline.medias().size());
        assertEquals("[图片][文件]", pipeline.summary());
    }
}
