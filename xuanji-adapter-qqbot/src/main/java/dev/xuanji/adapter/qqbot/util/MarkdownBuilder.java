package dev.xuanji.adapter.qqbot.util;

/**
 * QQ Markdown 消息构建器
 *
 * <p>用于构建 QQ 平台的 Markdown 格式消息，替代手动 JSON 拼接。
 * 自动处理 JSON 转义和格式包装，提供链式 API 简化消息构建。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 简单用法
 * String json = MarkdownBuilder.create()
 *     .h1("签到成功")
 *     .text("金币 +100")
 *     .text("经验 +20")
 *     .build();
 *
 * // 带格式
 * String json = MarkdownBuilder.create()
 *     .h2("银行账户")
 *     .bold("存款", "1000 金币")
 *     .bold("利率", "0.5%")
 *     .divider()
 *     .quote("输入 存款 金额 进行存款")
 *     .build();
 *
 * // 快捷模板
 * String json = MarkdownBuilder.successCard("签到成功", "金币 +100");
 * String json = MarkdownBuilder.rankingCard("排行榜", "用户A - 1000", "用户B - 800");
 * }</pre>
 *
 * @see KeyboardBuilder 与键盘按钮搭配使用
 * @see JsonEscUtil      JSON 转义工具
 */
public class MarkdownBuilder {

    /** Markdown 内容缓冲区 */
    private final StringBuilder sb = new StringBuilder();

    private MarkdownBuilder() {}

    /**
     * 创建 Markdown 构建器实例
     *
     * @return 新的 MarkdownBuilder 实例
     */
    public static MarkdownBuilder create() {
        return new MarkdownBuilder();
    }

    // ==================== 标题 ====================

    /** 一级标题 # */
    public MarkdownBuilder h1(String text) {
        sb.append("# ").append(esc(text)).append("\n\n");
        return this;
    }

    /** 二级标题 ## */
    public MarkdownBuilder h2(String text) {
        sb.append("## ").append(esc(text)).append("\n\n");
        return this;
    }

    /** 三级标题 ### */
    public MarkdownBuilder h3(String text) {
        sb.append("### ").append(esc(text)).append("\n\n");
        return this;
    }

    // ==================== 文本 ====================

    /** 普通文本（自动换行） */
    public MarkdownBuilder text(String text) {
        sb.append(esc(text)).append("\n");
        return this;
    }

    /** 普通文本（不换行，用于内联拼接） */
    public MarkdownBuilder raw(String text) {
        sb.append(text);
        return this;
    }

    /** 空行 */
    public MarkdownBuilder blankLine() {
        sb.append("\n");
        return this;
    }

    /** 换行（Markdown 语法：两个空格 + 换行） */
    public MarkdownBuilder br() {
        sb.append("  \n");
        return this;
    }

    // ==================== 格式化文本 ====================

    /** 加粗文本 **text** */
    public MarkdownBuilder bold(String text) {
        sb.append("**").append(esc(text)).append("**");
        return this;
    }

    /** 加粗标签 + 值: **label**: value */
    public MarkdownBuilder bold(String label, String value) {
        sb.append("**").append(esc(label)).append("**: ").append(esc(value)).append("\n");
        return this;
    }

    /** 斜体文本 _text_ */
    public MarkdownBuilder italic(String text) {
        sb.append("_").append(esc(text)).append("_");
        return this;
    }

    /** 删除线 ~~text~~ */
    public MarkdownBuilder strikethrough(String text) {
        sb.append("~~").append(esc(text)).append("~~");
        return this;
    }

    /** 行内代码 `code` */
    public MarkdownBuilder code(String text) {
        sb.append("`").append(esc(text)).append("`");
        return this;
    }

    // ==================== 引用 ====================

    /** 引用块 > text */
    public MarkdownBuilder quote(String text) {
        sb.append("> ").append(esc(text)).append("\n");
        return this;
    }

    /** 多行引用 */
    public MarkdownBuilder quote(String... lines) {
        for (String line : lines) {
            sb.append("> ").append(esc(line)).append("\n");
        }
        return this;
    }

    // ==================== 列表 ====================

    /** 无序列表项 - text */
    public MarkdownBuilder bullet(String text) {
        sb.append("- ").append(esc(text)).append("\n");
        return this;
    }

    /** 有序列表项（自动编号） */
    public MarkdownBuilder numbered(int num, String text) {
        sb.append(num).append(". ").append(esc(text)).append("\n");
        return this;
    }

    // ==================== 链接和图片 ====================

    /** 链接 [text](url) */
    public MarkdownBuilder link(String text, String url) {
        sb.append("[").append(esc(text)).append("](").append(url).append(")");
        return this;
    }

    /** 图片 ![alt](url) */
    public MarkdownBuilder image(String alt, String url) {
        sb.append("![").append(esc(alt)).append("](").append(url).append(")");
        return this;
    }

    // ==================== 分隔线 ====================

    /** 分隔线 *** */
    public MarkdownBuilder divider() {
        sb.append("\n***\n\n");
        return this;
    }

    // ==================== Emoji 快捷方法 ====================

    /** 成功提示 */
    public MarkdownBuilder success(String text) {
        sb.append("[INFO] ").append(esc(text)).append("\n");
        return this;
    }

    /** 错误提示 */
    public MarkdownBuilder error(String text) {
        sb.append("[ERROR] ").append(esc(text)).append("\n");
        return this;
    }

    /** 警告提示 */
    public MarkdownBuilder warn(String text) {
        sb.append("[WARN] ").append(esc(text)).append("\n");
        return this;
    }

    /** 信息提示 */
    public MarkdownBuilder info(String text) {
        sb.append("[INFO] ").append(esc(text)).append("\n");
        return this;
    }

    // ==================== 快捷模板 ====================

    /**
     * 成功提示卡片
     *
     * @param title   标题
     * @param content 内容
     * @return Markdown JSON 字符串
     */
    public static String successCard(String title, String content) {
        return create().h2("[OK] " + title).text(content).build();
    }

    /**
     * 错误提示卡片
     *
     * @param title   标题
     * @param content 内容
     * @return Markdown JSON 字符串
     */
    public static String errorCard(String title, String content) {
        return create().h2("[ERR] " + title).text(content).build();
    }

    /**
     * 信息展示卡片
     *
     * @param title 标题
     * @param lines 内容行
     * @return Markdown JSON 字符串
     */
    public static String infoCard(String title, String... lines) {
        MarkdownBuilder mb = create().h2("[INFO] " + title);
        for (String line : lines) {
            mb.text(line);
        }
        return mb.build();
    }

    /**
     * 列表展示卡片
     *
     * @param title 标题
     * @param items 列表项
     * @return Markdown JSON 字符串
     */
    public static String listCard(String title, String... items) {
        MarkdownBuilder mb = create().h2(title);
        for (int i = 0; i < items.length; i++) {
            mb.numbered(i + 1, items[i]);
        }
        return mb.build();
    }

    /**
     * 排行榜卡片
     *
     * @param title 排行榜标题
     * @param items 排行项（格式: "用户名 - 数值"）
     * @return Markdown JSON 字符串
     */
    public static String rankingCard(String title, String... items) {
        MarkdownBuilder mb = create().h2("[TOP] " + title).divider();
        for (int i = 0; i < items.length; i++) {
            String medal = switch (i) {
                case 0 -> "[1st]";
                case 1 -> "[2nd]";
                case 2 -> "[3rd]";
                default -> (i + 1) + ".";
            };
            mb.text(medal + " " + items[i]);
        }
        return mb.build();
    }

    /**
     * 用户画像卡片
     *
     * @param nickname 昵称
     * @param stats    统计数据（格式: "标签: 数值"）
     * @return Markdown JSON 字符串
     */
    public static String profileCard(String nickname, String... stats) {
        MarkdownBuilder mb = create().h2("[USER] " + nickname).divider();
        for (String stat : stats) {
            mb.text(stat);
        }
        return mb.build();
    }

    // ==================== 构建 ====================

    /**
     * 构建 Markdown JSON 字符串
     *
     * <p>生成的 JSON 结构：
     * <pre>
     * {"custom_template_id":null,"params":null,"content":"..."}
     * </pre>
     *
     * @return 完整的 markdown JSON 字符串
     */
    public String build() {
        String content = sb.toString().trim();
        return "{\"custom_template_id\":null,\"params\":null,\"content\":\"" + JsonEscUtil.esc(content) + "\"}";
    }

    /**
     * 构建纯 Markdown 内容（不含 JSON 包装）
     *
     * @return 纯 Markdown 文本
     */
    public String buildRaw() {
        return sb.toString().trim();
    }

    // ==================== 工具方法 ====================

    /**
     * 转义文本中的特殊字符
     *
     * <p>当前实现不对 Markdown 语法字符转义，仅处理 null 值。
     * JSON 转义在 {@link #build()} 中通过 {@link JsonEscUtil#esc} 处理。
     */
    private String esc(String text) {
        if (text == null) return "";
        return text;
    }

    /**
     * 格式化数字（添加千位分隔符）
     *
     * <pre>
     * formatNumber(1234567) -> "1,234,567"
     * </pre>
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * 格式化百分比
     *
     * <pre>
     * formatPercent(75.5) -> "75.5%"
     * </pre>
     */
    public static String formatPercent(double percent) {
        return String.format("%.1f%%", percent);
    }

    /**
     * 生成进度条
     *
     * <pre>
     * progressBar(7, 10) -> "#######___" (使用 ASCII 字符)
     * </pre>
     *
     * @param current 当前值
     * @param max     最大值
     * @return 10 字符宽的进度条字符串
     */
    public static String progressBar(int current, int max) {
        if (max <= 0) return "";
        int filled = Math.min(current * 10 / max, 10);
        return "#".repeat(filled) + "_".repeat(10 - filled);
    }
}
