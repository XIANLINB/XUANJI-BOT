package XuanJi.api.plugin;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * 璇玑插件生命周期基类 — 提供显式生命周期钩子，对齐 P2「插件真生命周期」目标。
 *
 * <p>插件主类继承此类后，可按需覆盖 {@link #onLoad()}/{@link #onEnable()}/
 * {@link #onDisable()}/{@link #onUnload()} 四个钩子：
 * <pre>
 *   PF4J start()  → onLoad()      （加载期，早于启用）
 *   管理器 enable  → onEnable()    （启动自动启用 或 运行时 enable）
 *   管理器 disable → onDisable()   （运行时 disable）
 *   PF4J stop()   → onUnload()     （卸载期）
 * </pre>
 *
 * <p>默认均为空实现，插件不覆盖也不会报错。
 */
public abstract class XuanJiPluginBase extends Plugin {

    public XuanJiPluginBase(PluginWrapper wrapper) {
        super(wrapper);
    }

    /** 插件被 PF4J 加载（start）时调用，早于 enable */
    public void onLoad() {}

    /** 插件启用时调用（启动自动启用 或 运行时 enable） */
    public void onEnable() {}

    /** 插件停用时调用（运行时 disable） */
    public void onDisable() {}

    /** 插件被 PF4J 卸载（stop）时调用 */
    public void onUnload() {}

    @Override
    public void start() {
        try {
            onLoad();
        } catch (Throwable t) {
            // 钩子异常不应阻断 PF4J 加载流程
            System.err.println("[XuanJiPlugin] onLoad 异常: " + t.getMessage());
        }
    }

    @Override
    public void stop() {
        try {
            onUnload();
        } catch (Throwable t) {
            System.err.println("[XuanJiPlugin] onUnload 异常: " + t.getMessage());
        }
    }
}
