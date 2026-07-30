package dev.xuanji.api.exception;

import lombok.Getter;

/**
 * 业务异常类
 *
 * <p>用于业务逻辑中的可预期错误，如 API 调用失败、参数校验不通过等。
 * 与系统异常（如 NullPointerException）区分，业务异常通常有明确的错误码和提示信息。
 *
 * <h3>使用场景</h3>
 * <ul>
 *   <li>{@link dev.xuanji.adapter.qq.api.QqApiService} — QQ 平台 API 调用失败时抛出</li>
 *   <li>事件 Handler — 业务逻辑校验失败时抛出</li>
 *   <li>由 {@link dev.xuanji.api.exception.GlobalExceptionHandler} 统一捕获并返回给前端</li>
 * </ul>
 *
 * <h3>错误码约定</h3>
 * <ul>
 *   <li>200 — 成功（不抛异常）</li>
 *   <li>400 — 请求参数错误</li>
 *   <li>401 — 未授权</li>
 *   <li>404 — 资源不存在</li>
 *   <li>429 — 频率限制</li>
 *   <li>500 — 服务器内部错误（默认）</li>
 * </ul>
 *
 * @see GlobalExceptionHandler 全局异常处理器，捕获本异常并返回统一格式
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 错误码（HTTP 状态码或业务错误码） */
    private final int code;

    /**
     * 构造函数（默认错误码 500）
     *
     * @param message 错误描述信息
     */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    /**
     * 构造函数（自定义错误码）
     *
     * @param code    错误码
     * @param message 错误描述信息
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
