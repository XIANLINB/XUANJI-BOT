package dev.xuanji.api.plugin;

import java.util.List;

/**
 * 插件配置字段声明 — 控制台「插件管理 → 配置」动态生成表单的数据源。
 *
 * <p>插件实现 {@link PluginConfigProvider} 返回本 record 列表，
 * 前端按 {@code type} 渲染对应输入控件（NUMBER→数字框、STRING→文本框、
 * BOOLEAN→开关、SELECT→下拉），未在控制台配置时读取 {@code defaultValue}。
 *
 * @param key          配置键（插件内唯一，如 coinPerCheckin）
 * @param label        中文名（表单标签，如「每次签到金币」）
 * @param type         控件类型
 * @param defaultValue 默认值（未配置时生效）
 * @param options      SELECT 类型的可选项；其他类型传空
 * @param description  说明文案（悬停/副文案展示）
 */
public record PluginConfigField(
        String key,
        String label,
        Type type,
        String defaultValue,
        List<String> options,
        String description) {

    /** 控件类型 — 前端按此渲染对应表单组件。 */
    public enum Type {
        /** 数字输入框（NInputNumber） */
        NUMBER,
        /** 文本输入框（NInput） */
        STRING,
        /** 开关（NSwitch） */
        BOOLEAN,
        /** 下拉选择（NSelect），配合 options */
        SELECT
    }
}
