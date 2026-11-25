package com.example.appservice.sdk;

public final class ReplayContext {

    private static final ThreadLocal<ReplayInfo> INFO = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> REPLAY = new ThreadLocal<>();

    private ReplayContext() {
    }

    public static boolean isReplay() {
        return INFO.get() != null || Boolean.TRUE.equals(REPLAY.get());
    }

    public static ReplayInfo get() {
        return INFO.get();
    }

    public static void set(ReplayInfo info) {
        INFO.set(info);
        REPLAY.set(info != null);
    }

    public static void setReplay(boolean replay) {
        if (replay) {
            REPLAY.set(true);
        } else {
            REPLAY.remove();
        }
    }

    public static String replayId() {
        ReplayInfo info = INFO.get();
        return info == null ? null : info.getReplayId();
    }

    public static void clear() {
        INFO.remove();
        REPLAY.remove();
    }
}
