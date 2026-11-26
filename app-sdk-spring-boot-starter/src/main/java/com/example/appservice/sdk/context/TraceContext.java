package com.example.appservice.sdk.context;

/**
 * 链路追踪 ThreadLocal 工具，串接入站流量与出站调用记录。
 */
public final class TraceContext {

    // ThreadLocal 保证同链路的入站与出站记录共享 traceId，请求结束后必须清理。
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    /**
     * 私有构造函数，防止工具类被实例化。
     */
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
