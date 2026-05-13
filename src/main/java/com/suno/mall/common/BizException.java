package com.suno.mall.common;

/**
 * 业务异常
 * <p>
 * 携带错误码的业务异常，由 {@link com.suno.mall.controller.GlobalExceptionHandler} 统一捕获
 * 并转换为带 errorCode 的 {@link ApiResponse}。
 *
 * <pre>
 *     // 使用示例
 *     throw new BizException("订单不存在: " + orderNo, ErrorCode.ORDER_NOT_FOUND);
 *     throw new BizException("参数不能为空", ErrorCode.PARAM_INVALID);
 * </pre>
 */
public class BizException extends RuntimeException {

    private final String errorCode;

    public BizException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
