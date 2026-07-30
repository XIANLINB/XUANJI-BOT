package com.qunxing.qq_bot_xuanji.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * QQ 消息按钮键盘构建器
 *
 * <p>用于构建 QQ 消息中的内联键盘（按钮组），支持多种按钮样式和行为。
 * 替代各插件中重复的 buildKeyboardJson() 方法。
 *
 * <h3>按钮样式 (style)</h3>
 * <ul>
 *   <li>0 — 灰线框</li>
 *   <li>1 — 蓝色填充（默认）</li>
 *   <li>2 — 蓝色线框</li>
 *   <li>3 — 蓝底白字</li>
 * </ul>
 *
 * <h3>按钮行为 (action.type)</h3>
 * <ul>
 *   <li>0 — 跳转按钮：打开 URL 或小程序</li>
 *   <li>1 — 回调按钮：回调后台接口，data 传给后台</li>
 *   <li>2 — 指令按钮（默认）：自动在输入框插入 @bot data</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基本用法 — 指令按钮
 * String json = KeyboardBuilder.create()
 *     .addButton("sign_in", "签到", "签到")
 *     .addButton("menu", "菜单", "菜单")
 *     .build();
 *
 * // 自动换行（每行最多5个）
 * String json = KeyboardBuilder.create()
 *     .addButton("b1", "按钮1")
 *     .addButton("b2", "按钮2")
 *     .addButton("b3", "按钮3")
 *     .build(5);
 *
 * // 跳转按钮
 * String json = KeyboardBuilder.create()
 *     .addLinkButton("help", "帮助", "https://example.com/help")
 *     .build();
 * }</pre>
 *
 * @see MarkdownBuilder 与 Markdown 搭配使用
 */
public class KeyboardBuilder {

    /** 按钮定义列表 */
    private final List<ButtonDef> buttons = new ArrayList<>();

    /**
     * 按钮定义内部类
     * <p>封装按钮的所有属性，包括 id、label、data、样式、行为等。
     */
    private static class ButtonDef {
        /** 按钮 ID（同一键盘内唯一） */
        final String id;

        /** 按钮文字（显示在按钮上） */
        final String label;

        /** 按钮数据（指令内容或回调数据） */
        final String data;

        /** 按钮样式：0=灰线框, 1=蓝填充, 2=蓝线框, 3=蓝底白字 */
        final int style;

        /** 按钮行为类型：0=跳转, 1=回调, 2=指令 */
        final int type;

        /** 指令按钮专属：点击后是否直接发送（true=自动发送，false=插入输入框） */
        final boolean enter;

        /** 指令按钮专属：是否带引用回复本消息 */
        final boolean reply;

        /** 锚点：0=无, 1=唤起选图器（仅单聊） */
        final int anchor;

        ButtonDef(String id, String label, String data, int style, int type,
                  boolean enter, boolean reply, int anchor) {
            this.id = id;
            this.label = label;
            this.data = data;
            this.style = style;
            this.type = type;
            this.enter = enter;
            this.reply = reply;
            this.anchor = anchor;
        }
    }

    private KeyboardBuilder() {}

    /**
     * 创建键盘构建器实例
     *
     * @return 新的 KeyboardBuilder 实例
     */
    public static KeyboardBuilder create() {
        return new KeyboardBuilder();
    }

    // ==================== 指令按钮（最常用） ====================

    /**
     * 添加指令按钮（data = label，默认样式）
     *
     * <p>点击后在输入框插入 @bot {label}，用户需手动发送。
     *
     * @param id    按钮 ID（同一键盘内唯一）
     * @param label 按钮文字（最多 10 字符）
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addButton(String id, String label) {
        buttons.add(new ButtonDef(id, label, label, 1, 2, false, false, 0));
        return this;
    }

    /**
     * 添加指令按钮（自定义 data）
     *
     * @param id    按钮 ID
     * @param label 按钮文字
     * @param data  指令内容（插入输入框的文本）
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addButton(String id, String label, String data) {
        buttons.add(new ButtonDef(id, label, data, 1, 2, false, false, 0));
        return this;
    }

    /**
     * 添加指令按钮（自定义 data + style）
     *
     * @param id    按钮 ID
     * @param label 按钮文字
     * @param data  指令内容
     * @param style 按钮样式：0=灰线框, 1=蓝填充, 2=蓝线框, 3=蓝底白字
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addButton(String id, String label, String data, int style) {
        buttons.add(new ButtonDef(id, label, data, style, 2, false, false, 0));
        return this;
    }

    // ==================== 高级指令按钮 ====================

    /**
     * 添加高级指令按钮
     *
     * <p>支持 enter（点击直接发送）和 reply（带引用回复）参数。
     * 仅单聊场景支持 enter=true。
     *
     * @param id     按钮 ID
     * @param label  按钮文字
     * @param data   指令内容
     * @param enter  点击后是否直接发送
     * @param reply  指令是否带引用回复本消息
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addCommandButton(String id, String label, String data,
                                            boolean enter, boolean reply) {
        buttons.add(new ButtonDef(id, label, data, 1, 2, enter, reply, 0));
        return this;
    }

    /**
     * 添加高级指令按钮（带样式）
     *
     * @param id     按钮 ID
     * @param label  按钮文字
     * @param data   指令内容
     * @param style  按钮样式
     * @param enter  点击后是否直接发送
     * @param reply  是否带引用回复
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addCommandButton(String id, String label, String data,
                                            int style, boolean enter, boolean reply) {
        buttons.add(new ButtonDef(id, label, data, style, 2, enter, reply, 0));
        return this;
    }

    /**
     * 添加选图按钮（仅单聊，版本 8983+）
     *
     * <p>点击后自动唤起手Q选图器，用户选择图片后自动发送。
     *
     * @param id    按钮 ID
     * @param label 按钮文字
     * @param data  指令内容
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addImageButton(String id, String label, String data) {
        buttons.add(new ButtonDef(id, label, data, 1, 2, false, false, 1));
        return this;
    }

    // ==================== 跳转按钮 ====================

    /**
     * 添加跳转按钮（打开 URL）
     *
     * @param id    按钮 ID
     * @param label 按钮文字
     * @param url   跳转 URL
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addLinkButton(String id, String label, String url) {
        buttons.add(new ButtonDef(id, label, url, 1, 0, false, false, 0));
        return this;
    }

    // ==================== 回调按钮 ====================

    /**
     * 添加回调按钮
     *
     * <p>点击后回调后台接口，data 会传给后台。
     *
     * @param id    按钮 ID
     * @param label 按钮文字
     * @param data  回调数据
     * @return 当前构建器实例（链式调用）
     */
    public KeyboardBuilder addCallbackButton(String id, String label, String data) {
        buttons.add(new ButtonDef(id, label, data, 1, 1, false, false, 0));
        return this;
    }

    // ==================== 构建 ====================

    /**
     * 构建键盘 JSON（所有按钮在一行）
     *
     * @return 键盘 JSON 字符串，按钮为空时返回 null
     */
    public String build() {
        return build(buttons.size());
    }

    /**
     * 构建键盘 JSON（指定每行最多按钮数）
     *
     * @param maxPerRow 每行最多按钮数（1-5，超出范围自动修正）
     * @return 键盘 JSON 字符串，按钮为空时返回 null
     */
    public String build(int maxPerRow) {
        if (buttons.isEmpty()) return null;
        if (maxPerRow < 1) maxPerRow = 1;
        if (maxPerRow > 5) maxPerRow = 5;

        StringBuilder kb = new StringBuilder("{\"content\":{\"rows\":[");
        int size = buttons.size();

        // 按 maxPerRow 分行渲染按钮
        for (int row = 0; row < size; row += maxPerRow) {
            if (row > 0) kb.append(",");
            kb.append("{\"buttons\":[");
            int end = Math.min(row + maxPerRow, size);

            for (int i = row; i < end; i++) {
                if (i > row) kb.append(",");
                ButtonDef btn = buttons.get(i);

                // render_data（按钮外观）
                kb.append("{\"id\":\"").append(JsonEscUtil.esc(btn.id))
                  .append("\",\"render_data\":{\"label\":\"").append(JsonEscUtil.esc(btn.label))
                  .append("\",\"visited_label\":\"").append(JsonEscUtil.esc(btn.label))
                  .append("\",\"style\":").append(btn.style).append("}");

                // action（按钮行为）
                kb.append(",\"action\":{\"type\":").append(btn.type)
                  .append(",\"data\":\"").append(JsonEscUtil.esc(btn.data))
                  .append("\",\"permission\":{\"type\":2}")  // type=2 表示所有人可用
                  .append(",\"unsupport_tips\":\"暂不支持\"");

                // 指令按钮专属参数
                if (btn.type == 2) {
                    if (btn.enter) kb.append(",\"enter\":true");
                    if (btn.reply) kb.append(",\"reply\":true");
                    if (btn.anchor == 1) kb.append(",\"anchor\":1");
                }

                kb.append("}}");
            }

            kb.append("]}");
        }

        kb.append("]}}");
        return kb.toString();
    }
}
