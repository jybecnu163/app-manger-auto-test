package com.example.appservice.sdk;

public final class ReplayContext {

    private static final ThreadLocal<Boolean> REPLAY = new ThreadLocal<>();

    private ReplayContext() {
    }

    public static boolean isReplay() {
        return Boolean.TRUE.equals(REPLAY.get());
    }

    public static void setReplay(boolean replay) {
        if (replay) {
            REPLAY.set(true);
        } else {
            REPLAY.remove();
        }
    }

    public static void clear() {
        REPLAY.remove();
    }
}
