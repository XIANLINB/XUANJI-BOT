package dev.xuanji.api.capability;

/**
 * 能力注册表 — 插件间共享能力的服务总线。
 *
 * <p>提供方向核心注入实现；消费方依赖接口即可取用。
 * 插件卸载时，其提供的能力自动回收。
 */
public interface ServiceRegistry {

    /** 注册一个能力实现（插件提供方调用） */
    <T> void provide(Class<T> type, T implementation);

    /** 获取一个能力实现（插件消费方调用；缺失时抛出 ServiceNotFoundException） */
    <T> T require(Class<T> type);

    /** 移除指定类型的能力 */
    <T> void remove(Class<T> type);

    /** 检查能力是否可用 */
    <T> boolean available(Class<T> type);

    /** 能力未找到时的异常 */
    final class ServiceNotFoundException extends RuntimeException {
        public ServiceNotFoundException(Class<?> type) {
            super("服务未找到: " + type.getSimpleName() + "，请安装提供该能力的插件");
        }
    }
}
