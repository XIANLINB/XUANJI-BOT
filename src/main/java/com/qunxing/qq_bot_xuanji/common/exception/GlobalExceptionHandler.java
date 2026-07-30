package com.qunxing.qq_bot_xuanji.common.exception;

import com.qunxing.qq_bot_xuanji.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * <p>统一捕获 Controller 层抛出的异常，转换为标准的 {@link R} 响应格式返回给前端。
 * 避免将异常堆栈直接暴露给客户端，同时保证日志中有完整的错误信息。
 *
 * <h3>处理的异常类型</h3>
 * <ul>
 *   <li>{@link BusinessException} — 业务异常，返回对应的错误码和消息</li>
 *   <li>{@link MethodArgumentNotValidException} — 参数校验失败（@Valid 注解）</li>
 *   <li>{@link BindException} — 参数绑定失败</li>
 *   <li>{@link MissingServletRequestParameterException} — 缺少必填参数</li>
 *   <li>{@link HttpRequestMethodNotSupportedException} — 不支持的 HTTP 方法</li>
 *   <li>{@link NoResourceFoundException} — 接口不存在（404）</li>
 *   <li>{@link Exception} — 兜底处理，返回 500</li>
 * </ul>
 *
 * @see BusinessException 自定义业务异常
 * @see R 统一响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e       业务异常
     * @param request HTTP 请求（用于记录请求路径）
     * @return 包含错误码和消息的响应
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {} - URI: {}", e.getCode(), e.getMessage(), request.getRequestURI());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 @Valid 参数校验异常
     *
     * <p>将所有字段错误信息用分号连接返回。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(400, message);
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return R.fail(400, message);
    }

    /**
     * 处理缺少必填参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(400, "缺少必填参数: " + e.getParameterName());
    }

    /**
     * 处理不支持的 HTTP 方法异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理资源不存在异常（404）
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(404, "接口不存在");
    }

    /**
     * 兜底异常处理 — 捕获所有未预期的异常
     *
     * <p>记录完整的异常堆栈到日志，但只返回通用的错误信息给客户端，
     * 避免泄露内部实现细节。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: URI={}, Message={}", request.getRequestURI(), e.getMessage(), e);
        return R.fail(500, "服务器内部错误");
    }
}
