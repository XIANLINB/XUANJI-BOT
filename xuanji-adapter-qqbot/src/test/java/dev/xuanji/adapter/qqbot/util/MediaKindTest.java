package dev.xuanji.adapter.qqbot.util;

import dev.xuanji.adapter.qqbot.converter.QqMessageConverter;
import dev.xuanji.api.message.MessageChain;
import dev.xuanji.api.message.MessageElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 富媒体类别判定回归 — 锁死线上实测问题：
 * QQ 发图片时 {@code content_type} 并非 {@code image/*}，若只按 MIME 判定会误判为「文件」，
 * 导致 {@code @MessageFilter(mediaTypes = IMAGE)} 收不到图片消息。
 */
class MediaKindTest {

    @Test
    void resolvesByContentTypeFirst() {
        assertEquals(MediaKind.IMAGE, MediaKind.resolve("image/jpeg", "x.bin"));
        assertEquals(MediaKind.VOICE, MediaKind.resolve("audio/silk", null));
        assertEquals(MediaKind.VIDEO, MediaKind.resolve("video/mp4", ""));
    }

    /** 线上实测场景：content_type 缺失/无意义，仅文件名可辨。 */
    @Test
    void fallsBackToFileExtensionWhenContentTypeUseless() {
        assertEquals(MediaKind.IMAGE, MediaKind.resolve("", "aovyw5.png"));
        assertEquals(MediaKind.IMAGE, MediaKind.resolve(null, "photo.JPEG"));
        assertEquals(MediaKind.IMAGE, MediaKind.resolve("file", "a.webp"));
        assertEquals(MediaKind.VOICE, MediaKind.resolve("", "note.amr"));
        assertEquals(MediaKind.VIDEO, MediaKind.resolve("", "clip.mov"));
    }

    @Test
    void unknownFallsBackToFile() {
        assertEquals(MediaKind.FILE, MediaKind.resolve("", "report.pdf"));
        assertEquals(MediaKind.FILE, MediaKind.resolve(null, null));
        assertEquals(MediaKind.FILE, MediaKind.resolve("", "noext"));
    }

    /** 端到端：真实报文形态（content 为空 + content_type 不可信）应解析出 Image 元素。 */
    @Test
    void payloadWithPngFilenameParsesAsImage() {
        String raw = """
                {"content":"","attachments":[
                  {"url":"https://example.com/a.png","filename":"aovyw5.png","size":119364}
                ]}""";

        MessageChain chain = QqMessageConverter.fromQqPayload(raw);

        assertTrue(chain.has(MessageElement.Image.class), "png 附件应解析为 Image 而非 File");
        assertFalse(chain.has(MessageElement.File.class), "不应再落到 File 分支");
        assertEquals("[图片]", chain.summary());
    }

    /** 日志摘要与消息链判定必须同口径，避免日志说「文件」、过滤器按「图片」命中。 */
    @Test
    void summaryLabelMatchesChainParsing() {
        assertEquals(" [图片x1]",
                MediaSummary.of(java.util.List.of(""), java.util.List.of("aovyw5.png")));
        assertEquals(" [文件x1]",
                MediaSummary.of(java.util.List.of(""), java.util.List.of("report.pdf")));
        assertEquals("", MediaSummary.of(java.util.List.of()));
    }
}
