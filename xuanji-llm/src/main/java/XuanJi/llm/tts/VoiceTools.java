package XuanJi.llm.tts;

import XuanJi.api.llm.LlmReplySink;
import XuanJi.api.llm.LlmTool;
import XuanJi.api.llm.LlmToolParam;
import XuanJi.llm.tool.LlmToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 语音工具 —— 接入 Fish Audio S2.1 Pro（主）+ 小米 MIMO（备），提供 send_voice 工具。
 *
 * <p>用户/AI 触发后：优先 Fish Audio（免费、中文好、不卡 quota），失败自动降级 MIMO；
 * 合成音频字节 → 落盘到本地 data/tts → 通过 {@link LlmReplySink#replyVoice} 发语音消息。
 */
@Slf4j
@Service
public class VoiceTools {

    /** TTS 输出落盘目录（相对当前工作目录）。 */
    private static final Path OUTPUT_DIR = Paths.get("data", "tts");

    private final FishAudioTtsService fishTts;
    private final MimoTtsService mimoTts;
    private final XuanJi.llm.config.LlmConfigStore configStore;
    private final XuanJi.llm.provider.CapabilityBindingResolver bindingResolver;
    private final List<LlmReplySink> sinks;

    public VoiceTools(FishAudioTtsService fishTts, MimoTtsService mimoTts,
                      XuanJi.llm.config.LlmConfigStore configStore,
                      XuanJi.llm.provider.CapabilityBindingResolver bindingResolver,
                      List<LlmReplySink> sinks) {
        this.fishTts = fishTts;
        this.mimoTts = mimoTts;
        this.configStore = configStore;
        this.bindingResolver = bindingResolver;
        this.sinks = sinks;
    }

    @LlmTool(name = "send_voice",
            descriptionZh = "TTS 语音合成并发送语音消息",
            description = "用 TTS 合成语音并发送给当前会话：用户要求'说一句/读一遍/语音回复'，或 AI 主动想发语音时调用。传入要说的话、可选音色/风格。返回是否成功发送",
            confirm = false)
    public String sendVoice(
            @LlmToolParam(name = "text", value = "要说的话（会被 TTS 朗读）") String text,
            @LlmToolParam(name = "voice", value = "音色（Fish 音色 ID 或 MIMO 音色名 冰糖/茉莉/苏打/白桦/Mia/Chloe/Milo/Dean），留空用配置默认", required = false) String voice,
            @LlmToolParam(name = "style", value = "风格控制（自然语言指令，如'清冷低沉的御姐音，语速慢'），留空用配置/人格默认", required = false) String style,
            LlmToolContext ctx) {
        // 语音多选绑定优先：按序尝试，第一个合成成功即用
        Object result = null;
        XuanJi.llm.config.LlmConfig cfg = configStore.get();
        List<TtsBinding> bindings = resolveTtsBindings(cfg);
        for (TtsBinding tb : bindings) {
            result = synthesizeBinding(tb, text, voice, style);
            if (result instanceof byte[]) {
                break;
            }
            log.warn("[TTS] 绑定 TTS {} 失败，尝试下一个: {}", tb.providerType(), result);
            result = null;
        }
        // 未配置 TTS 供应商 → 指引到供应商管理
        if (result == null) {
            result = "未配置可用的 TTS 供应商：请在「AI 能力 → 供应商管理」添加 Fish / 小米 MiMo 供应商并为其模型勾选 TTS 能力，再到「AI 设置 → 能力选择」绑定语音生成。";
        }
        if (result instanceof byte[] audio) {
            Path saved = saveWav(audio);
            boolean sent = false;
            String sentErr = null;
            if (ctx.event() != null && !sinks.isEmpty()) {
                for (LlmReplySink sink : sinks) {
                    try {
                        sink.replyVoice(ctx.event(), audio, "wav", text);
                        sent = true;
                        break;
                    } catch (UnsupportedOperationException e) {
                        sentErr = "当前平台不支持发送语音消息";
                    } catch (Exception e) {
                        sentErr = e.getMessage();
                        log.warn("[TTS] 发送语音失败: {}", e.getMessage());
                    }
                }
            }
            if (sent) {
                return "已向当前会话发送语音（AI 说：" + text + "，文件：" + saved + "）";
            }
            return "已合成语音文件（" + saved + "），但发送失败：" + (sentErr != null ? sentErr : "无可用发送通道");
        }
        return String.valueOf(result);
    }

    /** TTS 绑定：供应商类型 + 凭据 + 模型名。 */
    private record TtsBinding(String providerType, String apiKey, String baseUrl, String model) {}

    /** 解析 TTS 绑定列表（统一寻址：多选优先 → 单值，仅接受 fish/mimo 供应商类型）。 */
    private List<TtsBinding> resolveTtsBindings(XuanJi.llm.config.LlmConfig cfg) {
        List<TtsBinding> out = new java.util.ArrayList<>();
        for (XuanJi.llm.provider.CapabilityBindingResolver.CapBinding cb :
                bindingResolver.resolve(cfg, cfg.getTtsBindings(),
                        cfg.getTtsProviderId(), cfg.getTtsModelBinding(), "mimo-v2.5-tts")) {
            String type = cb.providerType();
            if (!"fish".equals(type) && !"mimo".equals(type)) {
                continue; // TTS 仅支持 Fish / 小米 MiMo 供应商
            }
            out.add(new TtsBinding(type, cb.creds().apiKey(), cb.creds().baseUrl(), cb.model()));
        }
        return out;
    }

    /** 按绑定类型分派合成。 */
    private Object synthesizeBinding(TtsBinding tb, String text, String voice, String style) {
        if ("fish".equals(tb.providerType())) {
            return fishTts.synthesizeExplicit(text, voice, style, tb.apiKey());
        }
        return mimoTts.synthesizeExplicit(text, voice, style, tb.apiKey(), tb.baseUrl(), tb.model());
    }

    /** 落盘 wav 到本地 data/tts 目录，返回绝对路径。 */
    private Path saveWav(byte[] audio) {
        try {
            Files.createDirectories(OUTPUT_DIR);
            String filename = "tts-" + System.currentTimeMillis() + "-" + Integer.toHexString(audio.hashCode()) + ".wav";
            Path file = OUTPUT_DIR.resolve(filename);
            try (FileOutputStream out = new FileOutputStream(file.toFile())) {
                out.write(audio);
            }
            log.info("[TTS] 合成语音已落盘: {} ({}B)", file, audio.length);
            return file.toAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("保存语音文件失败: " + e.getMessage(), e);
        }
    }
}