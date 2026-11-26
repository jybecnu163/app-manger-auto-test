package com.example.appservice.sdk;

/**
 * 链路追踪 ThreadLocal 工具，串接入站流量与出站调用记录。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    /**
     * 设置当前线程 traceId。
     */
    public static void set(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 获取当前线程 traceId。
     */
    public static String get() {
        return TRACE_ID.get();
    }

    /**
     * 清理当前线程 traceId。
     */
    public static void clear() {
        TRACE_ID.remove();
    }
}
