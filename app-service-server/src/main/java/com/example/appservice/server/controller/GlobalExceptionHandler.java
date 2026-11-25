package com.example.appservice.server.controller;

import com.example.appservice.common.dto.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        return Result.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}
