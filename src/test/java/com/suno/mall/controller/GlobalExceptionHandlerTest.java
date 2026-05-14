
package com.suno.mall.controller;

import com.suno.mall.common.ApiResponse;
import com.suno.mall.common.BizException;
import com.suno.mall.common.ErrorCode;
import com.suno.mall.controller.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

/**
 * 全局异常处理器测试
 */
public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    public void setUp() {
        globalExceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    public void testHandleBizException() {
        // 测试业务异常处理
        BizException ex = new BizException("业务错误", ErrorCode.PARAM_INVALID);
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBizException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("业务错误", response.getBody().message());
        assertEquals(ErrorCode.PARAM_INVALID, response.getBody().errorCode());
    }

    @Test
    public void testHandleIllegalArgument() {
        // 测试非法参数异常处理
        IllegalArgumentException ex = new IllegalArgumentException("参数错误");
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("参数错误", response.getBody().message());
        assertEquals(ErrorCode.PARAM_INVALID, response.getBody().errorCode());
    }

    @Test
    public void testHandleMethodArgumentNotValid() {
        // 测试方法参数校验异常处理
        org.springframework.validation.FieldError fieldError1 = mock(org.springframework.validation.FieldError.class);
        when(fieldError1.getField()).thenReturn("field1");
        when(fieldError1.getDefaultMessage()).thenReturn("错误1");

        org.springframework.validation.FieldError fieldError2 = mock(org.springframework.validation.FieldError.class);
        when(fieldError2.getField()).thenReturn("field2");
        when(fieldError2.getDefaultMessage()).thenReturn("错误2");

        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleMethodArgumentNotValid(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().message().contains("field1: 错误1"));
        assertTrue(response.getBody().message().contains("field2: 错误2"));
        assertEquals(ErrorCode.PARAM_INVALID, response.getBody().errorCode());
    }

    @Test
    public void testHandleBadCredentials() {
        // 测试凭据错误异常处理
        BadCredentialsException ex = new BadCredentialsException("用户名或密码错误");
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleBadCredentials(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("用户名或密码错误", response.getBody().message());
        assertEquals(ErrorCode.AUTH_BAD_CREDENTIALS, response.getBody().errorCode());
    }

    @Test
    public void testHandleAuthentication() {
        // 测试认证异常处理
        AuthenticationException ex = new AuthenticationException("认证失败") {};
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleAuthentication(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("认证失败，请重新登录", response.getBody().message());
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.getBody().errorCode());
    }

    @Test
    public void testHandleOptimisticLock() {
        // 测试乐观锁异常处理
        ObjectOptimisticLockingFailureException ex = new ObjectOptimisticLockingFailureException("乐观锁冲突") {};
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("操作过于频繁，请重试", response.getBody().message());
        assertEquals(ErrorCode.SYS_OPTIMISTIC_LOCK, response.getBody().errorCode());
    }

    @Test
    public void testHandleDataIntegrity() {
        // 测试数据完整性异常处理
        DataIntegrityViolationException ex = new DataIntegrityViolationException("数据完整性错误");
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrity(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("数据完整性错误，请检查输入参数", response.getBody().message());
        assertEquals(ErrorCode.DATA_INTEGRITY_VIOLATION, response.getBody().errorCode());
    }

    @Test
    public void testHandleUnknown() {
        // 测试未知异常处理
        Exception ex = new Exception("未知错误");
        try {
            ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleUnknown(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("系统异常，请稍后重试", response.getBody().message());
            assertEquals(ErrorCode.SYS_INTERNAL_ERROR, response.getBody().errorCode());
        } catch (Exception e) {
            System.err.println("测试过程中发生异常: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
