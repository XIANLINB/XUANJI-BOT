package XuanJi.llm.tts;

import XuanJi.llm.config.LlmConfig;
import XuanJi.llm.config.LlmConfigStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 语音合成服务 —— 小米 MIMO-V2.5-TTS（限时免费）。
 *
 * <p>同 ImageUnderstandService 模式：独立凭据（{@code tts*} 配置），调用
 * OpenAI 兼容协议 POST /chat/completions + {@code audio} 参数，返回
 * {@code choices[0].message.audio.data}（base64 编码音频字节）。
 *
 * <p>预置音色（mimo-v2.5-tts）：mimo_default / 冰糖 / 茉莉 / 苏打 / 白桦 / Mia / Chloe / Milo / Dean。
 * 风格控制：{@code user} 消息放自然语言指令；{@code assistant} 消息放合成文本。
 */
@Slf4j
@Service
public class MimoTtsService {

    /** TTS 单次调用超时（合成 + 下载 base64 音频，网络波动时给足）。 */
    private static final int TIMEOUT_SECONDS = 60;

    private final LlmConfigStore configStore;

    public MimoTtsService(LlmConfigStore configStore) {
        this.configStore = configStore;
    }

    /**
     * 合成语音（显式凭据版本）：apiKey/baseUrl/model 来自供应商表与能力绑定。
     *
     * @param text      要合成的文本（assistant content，会被模型朗读）
     * @param voice     音色（null/空 = 用配置默认）
     * @param stylePrompt 风格控制（null/空 = 用配置默认；非空覆盖）
     * @return 音频字节（wav），失败返回错误说明字符串（与图片理解一致的契约）
     */
    public Object synthesizeExplicit(String text, String voice, String stylePrompt,
                                     String apiKey, String baseUrl, String model) {
        if (text == null || text.isBlank()) {
            return "请提供要合成的文本";
        }
        LlmConfig cfg = configStore.get();
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 TTS 供应商 Key";
        }
        try {
            String useVoice = (voice == null || voice.isBlank()) ? cfg.getTtsVoice() : voice;
            String useStyle = (stylePrompt == null || stylePrompt.isBlank())
                    ? cfg.getTtsStylePrompt() : stylePrompt;
            String useFormat = (cfg.getTtsAudioFormat() == null || cfg.getTtsAudioFormat().isBlank())
                    ? "wav" : cfg.getTtsAudioFormat();
            String useModel = (model == null || model.isBlank()) ? "mimo-v2.5-tts" : model;

            RestClient client = buildClient(apiKey, baseUrl);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", useModel);

            // messages：user=风格控制(可选)，assistant=合成文本
            java.util.List<Map<String, Object>> messages = new java.util.ArrayList<>();
            if (useStyle != null && !useStyle.isBlank()) {
                messages.add(Map.of("role", "user", "content", useStyle));
            }
            messages.add(Map.of("role", "assistant", "content", text));
            body.put("messages", messages);

            Map<String, Object> audio = new LinkedHashMap<>();
            audio.put("format", useFormat);
            audio.put("voice", useVoice);
            body.put("audio", audio);

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractAudio(resp, useFormat);
        } catch (Exception e) {
            log.warn("[TTS] 合成失败: {}", e.getMessage());
            return "语音合成失败: " + friendlyError(e);
        }
    }

    /** 错误 → 可操作的提示（quota exhausted / 限流单独映射）。 */
    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        if (msg.contains("quota exhausted") || msg.contains("insufficient_quota")
                || msg.contains("Out of quota") || msg.contains("No quota")) {
            return "TTS 配额已耗尽（quota exhausted）。请到 platform.xiaomimimo.com 排查："
                    + "① Token Plan 订阅是否到期或 Credits 用尽；"
                    + "② Key 与 Base URL 需匹配（Token Plan 用 tp- 开头 Key + token-plan-cn.xiaomimimo.com/v1；按量付费用 sk- 开头 Key + api.xiaomimimo.com/v1）；"
                    + "③ 或检查套餐内 TTS 每日额度";
        }
        if (msg.contains("429") || msg.contains("Too Many")) {
            return "TTS 调用过频/限流，请稍后再试";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    /** 从响应中抽取 base64 音频字节。 */
    @SuppressWarnings("unchecked")
    private byte[] extractAudio(Map<String, Object> resp, String format) {
        if (resp == null) throw new IllegalStateException("TTS 响应为空");
        Object choicesObj = resp.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            Object error = resp.get("error");
            if (error instanceof Map<?, ?> em) {
                throw new IllegalStateException("TTS 调用失败: " + em.get("message"));
            }
            throw new IllegalStateException("TTS 响应缺少 choices");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) throw new IllegalStateException("TTS choices[0] 非法");
        Object messageObj = choice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) throw new IllegalStateException("TTS message 缺失");
        Object audioObj = message.get("audio");
        if (!(audioObj instanceof Map<?, ?> audioMap)) throw new IllegalStateException("TTS audio 缺失");
        Object data = audioMap.get("data");
        if (data == null) throw new IllegalStateException("TTS audio.data 缺失");
        return java.util.Base64.getDecoder().decode(String.valueOf(data));
    }

    private RestClient buildClient(String apiKey, String baseUrl) {
        String useBase = (baseUrl == null || baseUrl.isBlank())
                ? "https://token-plan-cn.xiaomimimo.com/v1" : baseUrl;
        return RestClient.builder()
                .baseUrl(useBase)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(jdkFactory())
                .build();
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        return f;
    }
}