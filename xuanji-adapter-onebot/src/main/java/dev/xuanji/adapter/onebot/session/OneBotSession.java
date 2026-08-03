package dev.xuanji.adapter.onebot.session;

/**
 * OneBot 会话 — 一条与 OneBot 实现（Napcat / Lagrange / go-cqhttp 等）之间的 WS 通道。
 *
 * <p>屏蔽"反向 WS 服务端连接"与"正向 WS 客户端连接"的差异：
 * 上层（API 服务、发送器）只管往会话里丢 JSON 文本，不关心方向。
 */
public interface OneBotSession {

    /** 该会话对端的机器人 QQ 号（OneBot 的 self_id） */
    String selfId();

    /** 连接方向标识：reverse（框架为服务端） / forward（框架为客户端） */
    String direction();

    /** 是否仍然可用 */
    boolean isOpen();

    /** 发送一段 JSON 文本（OneBot action 调用报文） */
    void sendText(String text);

    /** 主动关闭连接 */
    void close();
}
