package XuanJi.api.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 入站消息转换器注册表 — 平台适配器在启动时注册「报文 → XuanJiMessage」解析器。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>SDK 事件（{@code GroupMessageEvent}/{@code PrivateMessageEvent}）持有原始报文，
 *       插件调用 {@link #getChain()}（实际在事件类上）时按 <b>platform</b> 查找对应适配器注册的解析器，
 *       <b>首次调用才解析（懒解析）</b>，实现「管线归一化成果回灌给插件」。</li>
 *   <li>按平台分桶，支持多适配器并存（qqbot / onebot 各自注册自己的解析器）。</li>
 *   <li>解析失败兜底返回 {@link XuanJiMessage#EMPTY}，不抛异常拖垮插件。</li>
 * </ul>
 */
public final class MessageConverterHolder {

    private static final Map<String, Function<Object, XuanJiMessage>> CONVERTERS = new ConcurrentHashMap<>();

    private MessageConverterHolder() {}

    /** 适配器启动时注册入站解析器（key=platform，如 "qqbot"）。 */
    public static void register(String platform, Function<Object, XuanJiMessage> converter) {
        CONVERTERS.put(platform, converter);
    }

    /** 按平台解析原始报文为消息链；未注册平台或解析失败返回 {@link XuanJiMessage#EMPTY}。 */
    public static XuanJiMessage fromPayload(String platform, Object rawJson) {
        Function<Object, XuanJiMessage> c = CONVERTERS.get(platform);
        if (c == null) return XuanJiMessage.EMPTY;
        try {
            return c.apply(rawJson);
        } catch (Exception e) {
            return XuanJiMessage.EMPTY;
        }
    }
}
