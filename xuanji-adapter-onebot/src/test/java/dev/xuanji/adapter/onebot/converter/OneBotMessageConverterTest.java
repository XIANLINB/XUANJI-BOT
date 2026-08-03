package dev.xuanji.adapter.onebot.converter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.xuanji.api.json.Json;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息段双向转换单测 —— 验证 MessageChain 在 OneBot 与框架之间往返不失真。
 *
 * <p>覆盖 OneBot v11 全部常见段及 Napcat 扩展（markdown），以及无损透传段的往返。
 */
class OneBotMessageConverterTest {

    @Test
    @DisplayName("接收：常见消息段应映射到对应 MessageElement")
    void segmentsToChain() {
        String msg = """
                [
                  {"type":"text","data":{"text":"看图"}},
                  {"type":"image","data":{"file":"abc.jpg","url":"https://x/abc.jpg","file_size":1024}},
                  {"type":"face","data":{"id":"178"}},
                  {"type":"at","data":{"qq":"all"}},
                  {"type":"reply","data":{"id":"555"}},
                  {"type":"record","data":{"file":"v.amr","url":"https://x/v.amr"}}
                ]
                """;

        MessageChain chain = OneBotMessageConverter.toChain(Json.parse(msg));

        assertEquals(6, chain.elements().size());
        assertEquals("看图", chain.plainText());

        MessageElement.Image img = (MessageElement.Image) chain.elements().get(1);
        assertEquals("https://x/abc.jpg", img.url(), "url 优先于 file");
        assertEquals(1024, img.size());

        assertEquals(178, ((MessageElement.Face) chain.elements().get(2)).faceId());

        MessageElement.At at = (MessageElement.At) chain.elements().get(3);
        assertEquals("all", at.userId(), "@全体应识别为 At.all()");

        assertEquals("555", ((MessageElement.Reply) chain.elements().get(4)).targetMsgId());
        assertEquals("https://x/v.amr", ((MessageElement.Voice) chain.elements().get(5)).url());
    }

    @Test
    @DisplayName("接收：Napcat markdown 段应映射为 Markdown 元素")
    void markdownSegmentToElement() {
        MessageChain chain = OneBotMessageConverter.toChain(
                Json.parse("[{\"type\":\"markdown\",\"data\":{\"content\":\"# 标题\\n正文\"}}]"));
        assertEquals(1, chain.elements().size());
        MessageElement.Markdown md = (MessageElement.Markdown) chain.elements().get(0);
        assertEquals("# 标题\n正文", md.content());
    }

    @Test
    @DisplayName("接收：unknown/扩展段（dice/poke/xml 等）应整段透传而非丢弃")
    void unknownSegmentPassthrough() {
        MessageChain chain = OneBotMessageConverter.toChain(
                Json.parse("[{\"type\":\"dice\",\"data\":{\"result\":\"6\"}}]"));
        assertEquals(1, chain.elements().size());
        MessageElement.Passthrough p = (MessageElement.Passthrough) chain.elements().get(0);
        assertEquals("onebot", p.platform());
        assertEquals("dice", p.description());
        assertTrue(p.nativePayload().toString().contains("\"type\":\"dice\""));
    }

    @Test
    @DisplayName("接收：戳一戳段应透传并保留原始 JSON")
    void pokeSegmentPassthrough() {
        MessageChain chain = OneBotMessageConverter.toChain(
                Json.parse("[{\"type\":\"poke\",\"data\":{\"qq\":\"30003\",\"id\":1,\"name\":\"戳一戳\"}}]"));
        MessageElement.Passthrough p = (MessageElement.Passthrough) chain.elements().get(0);
        assertEquals("poke", p.description());
        assertTrue(p.nativePayload().toString().contains("\"name\":\"戳一戳\""));
    }

    @Test
    @DisplayName("发送：MessageChain 应输出标准数组格式消息段")
    void chainToSegments() {
        MessageChain chain = MessageChain.builder()
                .reply("555")
                .at("30003")
                .text("你好")
                .image("https://x/a.png")
                .face(178)
                .build();

        ArrayNode arr = OneBotMessageConverter.toSegments(chain);

        assertEquals(5, arr.size());
        assertEquals("reply", arr.get(0).path("type").asText());
        assertEquals("555", arr.get(0).path("data").path("id").asText());
        assertEquals("at", arr.get(1).path("type").asText());
        assertEquals("30003", arr.get(1).path("data").path("qq").asText());
        assertEquals("你好", arr.get(2).path("data").path("text").asText());
        assertEquals("image", arr.get(3).path("type").asText());
        assertEquals("https://x/a.png", arr.get(3).path("data").path("file").asText());
        assertEquals("178", arr.get(4).path("data").path("id").asText());
    }

    @Test
    @DisplayName("发送：Markdown 应输出原生 markdown 段（Napcat）而不是降级文本")
    void markdownElementToSegment() {
        MessageChain chain = MessageChain.builder().markdown("# 标题").build();
        ArrayNode arr = OneBotMessageConverter.toSegments(chain);
        assertEquals(1, arr.size());
        assertEquals("markdown", arr.get(0).path("type").asText());
        assertEquals("# 标题", arr.get(0).path("data").path("content").asText());
    }

    @Test
    @DisplayName("发送：OneBot 不支持的键盘元素应被丢弃，其余保留")
    void unsupportedElementsDegrade() {
        MessageChain chain = MessageChain.builder()
                .markdown("# 标题")
                .add(new MessageElement.Keyboard("{\"rows\":[]}"))
                .text("尾部")
                .build();

        ArrayNode arr = OneBotMessageConverter.toSegments(chain);

        assertEquals(2, arr.size(), "键盘被丢弃，Markdown 输出原生 markdown 段");
        assertEquals("markdown", arr.get(0).path("type").asText());
        assertEquals("# 标题", arr.get(0).path("data").path("content").asText());
        assertEquals("尾部", arr.get(1).path("data").path("text").asText());
    }

    @Test
    @DisplayName("往返：数组 → Chain → 数组 关键字段不丢失")
    void roundTrip() {
        String original = """
                [{"type":"text","data":{"text":"hi"}},{"type":"at","data":{"qq":"30003"}}]
                """;

        MessageChain chain = OneBotMessageConverter.toChain(Json.parse(original));
        ArrayNode back = OneBotMessageConverter.toSegments(chain);

        assertEquals(2, back.size());
        assertEquals("hi", back.get(0).path("data").path("text").asText());
        assertEquals("30003", back.get(1).path("data").path("qq").asText());
    }

    @Test
    @DisplayName("往返：透传段（dice/poke/xml）原样还原不丢字段")
    void passthroughRoundTrip() {
        String original = "[{\"type\":\"dice\",\"data\":{\"result\":\"6\"}},"
                + "{\"type\":\"poke\",\"data\":{\"qq\":\"30003\",\"name\":\"戳一戳\"}},"
                + "{\"type\":\"xml\",\"data\":{\"data\":\"<xml/>\"}}]";

        MessageChain chain = OneBotMessageConverter.toChain(Json.parse(original));
        ArrayNode back = OneBotMessageConverter.toSegments(chain);

        assertEquals(3, back.size());
        assertEquals("dice", back.get(0).path("type").asText());
        assertEquals("6", back.get(0).path("data").path("result").asText());
        assertEquals("poke", back.get(1).path("type").asText());
        assertEquals("戳一戳", back.get(1).path("data").path("name").asText());
        assertEquals("xml", back.get(2).path("type").asText());
    }

    @Test
    @DisplayName("往返：Markdown 元素 ↔ markdown 段")
    void markdownRoundTrip() {
        MessageChain chain = MessageChain.builder().markdown("# 标题").build();
        ArrayNode arr = OneBotMessageConverter.toSegments(chain);
        MessageChain back = OneBotMessageConverter.toChain(arr);
        MessageElement.Markdown md = (MessageElement.Markdown) back.elements().get(0);
        assertEquals("# 标题", md.content());
    }

    @Test
    @DisplayName("CQ 码转义字符应正确还原")
    void cqUnescape() {
        MessageChain chain = OneBotMessageConverter.toChain(
                Json.parse("\"a&#91;b&#93;c&#44;d&amp;e\""));
        assertEquals("a[b]c,d&e", chain.plainText());
    }

    @Test
    @DisplayName("空消息与 null 不应抛异常")
    void emptyInputs() {
        assertTrue(OneBotMessageConverter.toChain(null).elements().isEmpty());
        assertTrue(OneBotMessageConverter.toChain(Json.parse("[]")).elements().isEmpty());
        assertTrue(OneBotMessageConverter.toSegments(null).isEmpty());
        assertTrue(OneBotMessageConverter.toSegments(MessageChain.EMPTY).isEmpty());
    }
}
