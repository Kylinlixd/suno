package com.suno.mall.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一 API 响应体
 *
 * @param success   是否成功
 * @param message   人类可读的消息（成功时为 "OK"，失败时为错误描述）
 * @param errorCode 错误码（成功时为 null，失败时为业务错误码，便于前端做差异化处理）
 * @param data      响应数据
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, String errorCode, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null, null);
    }

    public static <T> ApiResponse<T> fail(String message, String errorCode) {
        return new ApiResponse<>(false, message, errorCode, null);
    }

    public static <T> ApiResponse<T> failWithCode(String errorCode, String message) {
        return new ApiResponse<>(false, message, errorCode, null);
    }
}
