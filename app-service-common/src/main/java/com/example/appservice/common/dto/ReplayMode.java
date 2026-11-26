package com.example.appservice.common.dto;

/**
 * 回放模式枚举：NONE 表示正常调用，DRY_RUN 表示事务回滚的干跑回放。
 */
public enum ReplayMode {
    NONE,
    DRY_RUN
}
