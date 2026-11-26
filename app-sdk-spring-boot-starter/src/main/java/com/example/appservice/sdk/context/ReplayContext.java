package com.example.appservice.sdk.context;

/**
 * 回放上下文 ThreadLocal 工具，保存回放信息并供过滤器、拦截器和任务装饰器共享。
 */
public final class ReplayContext {

    private static final ThreadLocal<ReplayInfo> INFO = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAY = new ThreadLocal<>();

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private ReplayContext() {
    }

    /**
     * 判断当前线程是否处于回放态。
     */
    public static boolean isReplay() {
        return INFO.get() != null || Boolean.TRUE.equals(REPLAY.get());
    }

    /**
     * 获取当前线程的回放信息。
     */
    public static ReplayInfo get() {
        return INFO.get();
    }

    /**
     * 设置当前线程回放信息。
     */
    public static void set(ReplayInfo info) {
        INFO.set(info);
        REPLAY.set(info != null);
    }

    /**
     * 仅设置回放标志，用于无法携带完整回放头的场景。
     */
    public static void setReplay(boolean replay) {
        if (replay) {
            REPLAY.set(true);
        } else {
            REPLAY.remove();
        }
    }

    /**
     * 返回当前回放 ID。
     */
    public static String replayId() {
        ReplayInfo info = INFO.get();
        return info == null ? null : info.getReplayId();
    }

    /**
     * 清理当前线程回放上下文。
     */
    public static void clear() {
        // 回放结束后必须清理 ThreadLocal，避免污染线程池中的普通请求。
        INFO.remove();
        REPLAY.remove();
    }
}
