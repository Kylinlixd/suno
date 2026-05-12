package com.recycle.mall.controller;

import com.recycle.mall.common.ApiResponse;
import com.recycle.mall.common.BizException;
import com.recycle.mall.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Objects;

/**
 * 全局异常处理器
 * <p>
 * 将各类异常统一转换为 {@link ApiResponse} 结构，携带 errorCode 便于前端差异化处理。
 * 同时对未预期异常记录 WARN 日志，避免静默吞错。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 业务异常（携带 errorCode） ====================

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode()));
    }

    // ==================== 参数校验 ====================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage(), ErrorCode.PARAM_INVALID));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + Objects.toString(err.getDefaultMessage(), "参数不合法"))
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, ErrorCode.PARAM_INVALID));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> {
                    // 仅取属性名，不暴露完整方法路径
                    String path = v.getPropertyPath().toString();
                    String fieldName = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return fieldName + ": " + v.getMessage();
                })
                .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.fail(message, ErrorCode.PARAM_INVALID));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("缺少必填参数: " + ex.getParameterName(), ErrorCode.PARAM_MISSING));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "未知类型";
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("参数类型错误: " + ex.getName() + " 应为 " + requiredType, ErrorCode.PARAM_TYPE_MISMATCH));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("请求体格式错误，请检查 JSON 内容", ErrorCode.PARAM_BODY_MALFORMED));
    }

    // ==================== 认证鉴权 ====================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("用户名或密码错误", ErrorCode.AUTH_BAD_CREDENTIALS));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("认证失败，请重新登录", ErrorCode.AUTH_UNAUTHORIZED));
    }

    // ==================== 并发与数据冲突 ====================

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("操作过于频繁，请重试", ErrorCode.SYS_OPTIMISTIC_LOCK));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("数据冲突，请检查请求后重试", ErrorCode.PAYMENT_DATA_CONFLICT));
    }

    // ==================== 状态异常 ====================

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("系统处理异常，请稍后重试", ErrorCode.SYS_INTERNAL_ERROR));
    }

    // ==================== 兜底 ====================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("未预期异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("系统异常，请稍后重试", ErrorCode.SYS_INTERNAL_ERROR));
    }
}
