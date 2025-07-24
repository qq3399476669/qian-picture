package com.siqian.qianpicturebackend.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.siqian.qianpicturebackend.common.BaseResponse;
import com.siqian.qianpicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice  // controller增强器，可以全局处理异常
public class GlobalExceptionHandler {

    // -------------------以下两个异常为sa token权限框架抛出的异常----------------
    // 需要与自定义异常一样进行统一异常处理
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.fail(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.fail(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }
    // ---------------------------------------------------------------------

    /**
     * 专门处理自定义业务异常
     * @param e
     * @return
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> handleBusinessException(BusinessException e) {
        log.error("BusinessException: {}", e.getMessage());
        return ResultUtils.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> handleRuntimeException(RuntimeException e) {
        log.error("RuntimeException: {}", e.getMessage());
        return ResultUtils.fail(ErrorCode.SYSTEM_ERROR, "系统异常");
    }
}
