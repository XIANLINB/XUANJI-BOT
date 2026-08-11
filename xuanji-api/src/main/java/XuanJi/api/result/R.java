package XuanJi.api.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应结果封装
 *
 * <p>所有 REST API 接口的统一返回格式，确保前端可以用一致的方式处理响应。
 *
 * <h3>JSON 结构</h3>
 * <pre>
 * {
 *   "code": 200,          // 状态码（200=成功，其他=失败）
 *   "message": "success", // 提示信息
 *   "data": { ... },      // 业务数据（可为 null）
 *   "timestamp": 1234567  // 响应时间戳（毫秒）
 * }
 * </pre>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 成功响应
 * return R.ok(data);
 * return R.ok("操作成功", data);
 *
 * // 失败响应
 * return R.fail("参数错误");
 * return R.fail(400, "缺少必填参数");
 * }</pre>
 *
 * @see XuanJi.api.exception.GlobalExceptionHandler 使用 R 返回错误响应
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态码：200=成功，400=参数错误，401=未授权，500=服务器错误 */
    private int code;

    /** 提示信息（成功时为 "success"，失败时为错误描述） */
    private String message;

    /** 业务数据（可为 null，成功时包含实际数据） */
    private T data;

    /** 响应时间戳（毫秒），用于前端判断响应时效性 */
    private long timestamp;

    /** 默认构造函数 */
    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 全参数构造函数
     *
     * @param code    状态码
     * @param message 提示信息
     * @param data    业务数据
     */
    public R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return code=200, message="success", data=null
     */
    public static <T> R<T> ok() {
        return new R<>(200, "success", null);
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code=200, message="success", data=传入的数据
     */
    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    /**
     * 成功响应（自定义消息和数据）
     *
     * @param message 提示信息
     * @param data    业务数据
     * @param <T>     数据类型
     * @return code=200, 自定义消息和数据
     */
    public static <T> R<T> ok(String message, T data) {
        return new R<>(200, message, data);
    }

    /**
     * 失败响应（默认错误码 500）
     *
     * @param message 错误描述
     * @param <T>     数据类型
     * @return code=500, data=null
     */
    public static <T> R<T> fail(String message) {
        return new R<>(500, message, null);
    }

    /**
     * 失败响应（自定义错误码）
     *
     * @param code    错误码
     * @param message 错误描述
     * @param <T>     数据类型
     * @return 自定义错误码, data=null
     */
    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
