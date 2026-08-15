package XuanJi.core.command;

/**
 * 参数解析失败（缺参 / 类型无法解析）时由 {@link CommandRegistry#resolveArgs} 抛出，
 * 由分发逻辑捕获后作为「指令回复文案」返回给用户（而非原 hack 那种碰巧塞一个字符串进方法参数）。
 *
 * <p>属于框架内部异常，插件无需感知。
 */
public class MissingArgException extends RuntimeException {
    public MissingArgException(String message) {
        super(message);
    }
}
