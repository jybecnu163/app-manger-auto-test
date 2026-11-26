package com.example.appservice.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装：code=0 表示成功，code=1 表示失败，data 承载业务数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    /**
     * 构造成功响应，固定 code=0、message="ok"。
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    /**
     * 构造失败响应，固定 code=1，并清空业务数据。
     */
    public static <T> Result<T> fail(String message) {
        return new Result<>(1, message, null);
    }
}
