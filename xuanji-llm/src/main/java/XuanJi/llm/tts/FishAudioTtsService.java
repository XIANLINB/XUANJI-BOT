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
import java.util.Map;

/**
 * Fish Audio S2.1 Pro（免费）语音合成 —— 璇玑 TTS 主通道。
 *
 * <p>S2.1 Pro free 免费窗口至 2026-08-31（fair use 无限量），中文质量一流、支持声音克隆。
 * 接口：POST /v1/tts，{@code model: "s2.1-pro-free"} 走 header；
 * 风格控制：把自然语言描述放进文本的 {@code [方括号]} 情感控制（S2 系列语法）；
 * 音色：{@code reference_id}（音色库/克隆音色 ID）。
 *
 * <p>与 {@link MimoTtsService} 同契约：成功返回音频字节，失败返回错误说明字符串。
 */
@Slf4j
@Service
public class FishAudioTtsService {

    private static final String BASE_URL = "https://api.fish.audio/v1";
    private static final String FREE_MODEL = "s2.1-pro-free";

    /** 单次合成超时（免费档无 SLA，读超时给足 60s）。 */
    private static final int TIMEOUT_SECONDS = 60;

    /** 连接超时（海外服务网络波动大，给足 20s）。 */
    private static final int CONNECT_TIMEOUT_SECONDS = 20;

    /** 网络类错误最大重试次数（连接超时/IO 错误常见瞬时故障，重试一次）。 */
    private static final int NETWORK_RETRIES = 1;

    /** 重试间隔毫秒。 */
    private static final long RETRY_DELAY_MS = 2000;

    private final LlmConfigStore configStore;

    public FishAudioTtsService(LlmConfigStore configStore) {
        this.configStore = configStore;
    }

    /**
     * 合成语音（显式凭据版本）：apiKey 来自供应商表与能力绑定。
     *
     * @param text        要合成的文本
     * @param voice       Fish reference_id（音色 ID；空 = 用配置默认）
     * @param stylePrompt 自然语言风格描述（空 = 用配置默认；非空覆盖，拼进文本 [方括号] 情感控制）
     */
    public Object synthesizeExplicit(String text, String voice, String stylePrompt, String apiKey) {
        if (text == null || text.isBlank()) {
            return "请提供要合成的文本";
        }
        LlmConfig cfg = configStore.get();
        if (apiKey == null || apiKey.isBlank()) {
            return "未配置 TTS 供应商 Key";
        }
        try {
            String style = (stylePrompt == null || stylePrompt.isBlank())
                    ? cfg.getFishStylePrompt() : stylePrompt;
            String finalText = (style == null || style.isBlank())
                    ? text : "[" + style + "] " + text;
            String voiceId = (voice == null || voice.isBlank()) ? cfg.getFishVoice() : voice;

            RestClient client = buildClient(apiKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", finalText);
            if (voiceId != null && !voiceId.isBlank()) {
                body.put("reference_id", voiceId);
            }
            String format = (cfg.getTtsAudioFormat() == null || cfg.getTtsAudioFormat().isBlank())
                    ? "wav" : cfg.getTtsAudioFormat();
            body.put("format", format);
            if (cfg.getFishSpeed() > 0 && Math.abs(cfg.getFishSpeed() - 1.0) > 0.001) {
                body.put("prosody", Map.of("speed", cfg.getFishSpeed()));
            }

            // 网络类错误（连接超时/IO 波动）自动重试一次
            int attempt = 0;
            while (true) {
                try {
                    byte[] audio = client.post()
                            .uri("/tts")
                            .header("model", FREE_MODEL)
                            .body(body)
                            .retrieve()
                            .body(byte[].class);
                    if (audio == null || audio.length == 0) {
                        return "语音合成失败: 空响应";
                    }
                    log.info("[TTS-FISH] 合成成功: {}B", audio.length);
                    return audio;
                } catch (Exception e) {
                    if (isNetworkError(e) && attempt < NETWORK_RETRIES) {
                        attempt++;
                        log.warn("[TTS-FISH] 网络异常，{} 次重试（{}ms）: {}", attempt, RETRY_DELAY_MS, e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return "语音合成失败: " + friendlyError(e);
                        }
                        continue;
                    }
                    log.warn("[TTS-FISH] 合成失败: {}", e.getMessage());
                    return "语音合成失败: " + friendlyError(e);
                }
            }
        } catch (Exception e) {
            log.warn("[TTS-FISH] 合成失败: {}", e.getMessage());
            return "语音合成失败: " + friendlyError(e);
        }
    }

    /** 网络层错误（连接超时/IO 异常）才重试；业务错误（401/402/429/400）不重试。 */
    private boolean isNetworkError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("connect timed out") || msg.contains("I/O error")
                || msg.contains("Connection refused") || msg.contains("Read timed out")
                || msg.contains("Connection reset") || msg.contains("timed out")
                || e instanceof org.springframework.web.client.ResourceAccessException;
    }

    /** 错误 → 可操作提示。 */
    private String friendlyError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return e.getClass().getSimpleName();
        if (msg.contains("402") || msg.contains("out of credits") || msg.contains("quota")) {
            return "Fish Audio 余额/额度不足，请到 fish.audio 检查账单";
        }
        if (msg.contains("401") || msg.contains("invalid api key")) {
            return "Fish Audio Key 无效，请检查 fish.audio/app/api-keys";
        }
        if (msg.contains("400") && msg.contains("reference_id")) {
            return "Fish 音色 reference_id 无效，请检查音色 ID 是否正确";
        }
        if (msg.contains("429") || msg.contains("Too Many")) {
            return "Fish Audio 调用过频/限流，请稍后再试";
        }
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    private RestClient buildClient(String apiKey) {
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(jdkFactory())
                .build();
    }

    private static JdkClientHttpRequestFactory jdkFactory() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        return f;
    }
}