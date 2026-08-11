package XuanJi.api.plugin;

/**
 * 群管命令执行结果 — 携带成功/失败状态与可读信息（成功提示或错误原因）。
 *
 * <p>插件命令可直接将 {@code message} 回复给用户：成功时如「已禁言 5 分钟」，
 * 失败时如「禁言被拒：机器人必须为群管理」。错误信息由框架/平台适配器提供。
 */
public record OpResult(boolean ok, String message) {

    public static OpResult ok(String message) {
        return new OpResult(true, message == null || message.isBlank() ? "操作成功" : message);
    }

    public static OpResult fail(String message) {
        return new OpResult(false, message == null || message.isBlank() ? "操作失败" : message);
    }

    /** 是否成功。 */
    public boolean ok() {
        return ok;
    }

    /** 成功提示或错误原因（可直接面向用户）。 */
    public String message() {
        return message;
    }
}
