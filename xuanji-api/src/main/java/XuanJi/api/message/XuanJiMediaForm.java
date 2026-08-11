package XuanJi.api.message;

/**
 * 媒体引用形态 — 五态归一化的核心枚举。
 *
 * <p>不同平台（QQ 官方 / OneBot）把同一份媒体以不同形态交给框架：
 * <ul>
 *   <li>{@link #URL}         — http(s):// 网络链接（含 QQ 临时链接裸域名）</li>
 *   <li>{@link #FILE_PATH}   — 本地文件 / file:// 协议</li>
 *   <li>{@link #BASE64}      — base64:// 或裸 base64 二进制</li>
 *   <li>{@link #DATA_URI}    — data:image/png;base64,... 数据 URI</li>
 *   <li>{@link #PLATFORM_ID} — 平台文件标识（QQ file_info / OneBot file_id）</li>
 * </ul>
 *
 * <p>归一化目标：插件只需读取 {@code XuanJiMediaRef.form()} 即可知道引用是哪种形态，
 * 无需为每个平台写不同的下载 / 取用逻辑（参考 AstrBot convert_to_file_path 理念）。
 */
public enum XuanJiMediaForm {
    /** 网络链接（http/https，含 QQ 裸域名临时链接） */
    URL,
    /** 本地文件或 file:// 协议 */
    FILE_PATH,
    /** base64:// 或裸 base64 二进制串 */
    BASE64,
    /** data:image/png;base64,... 数据 URI */
    DATA_URI,
    /** 平台文件标识（QQ file_info / OneBot file_id 哈希） */
    PLATFORM_ID
}
