package dev.xuanji.core.plugin;

/**
 * 璇玑插件生命周期状态机。
 *
 * <pre>
 *   LOADED ──enable──▶ ENABLED ──disable──▶ DISABLED ──enable──▶ ENABLED
 *     │                                                                        │
 *     └───────────────────────── unload ───────────────────────────────────┘
 *   ERROR：加载或生命周期钩子执行异常
 * </pre>
 *
 * <p>注：与 {@code org.pf4j.PluginState} 区分（后者描述 PF4J 内部的 resolved/started/stopped）。
 * 此处仅描述「启用/停用」这一业务层状态。
 */
public enum XuanjiPluginState {
    /** 已加载（PF4J resolved/started），尚未按持久态应用启用/停用 */
    LOADED,
    /** 已启用（指令已注册、钩子已 onEnable） */
    ENABLED,
    /** 已停用（指令已反注册、钩子已 onDisable） */
    DISABLED,
    /** 加载或生命周期异常 */
    ERROR
}
