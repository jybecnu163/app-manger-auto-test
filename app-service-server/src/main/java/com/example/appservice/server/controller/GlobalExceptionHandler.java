package com.example.appservice.server.controller;

import com.example.appservice.common.dto.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将未捕获异常统一包装为 Result.fail 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 兜底处理所有异常并返回统一错误结构。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        return Result.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
