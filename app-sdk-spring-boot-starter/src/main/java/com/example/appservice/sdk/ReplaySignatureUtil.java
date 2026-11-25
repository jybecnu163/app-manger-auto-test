package com.example.appservice.sdk;

import com.example.appservice.common.util.ReplaySignatureCalculator;

public final class ReplaySignatureUtil {

    private ReplaySignatureUtil() {
    }

    public static String sign(String secret, ReplayInfo info) {
        return ReplaySignatureCalculator.sign(secret, info.getReplayId(), info.getReplayMode().name(),
                String.valueOf(info.getTimestamp()), info.getNonce());
    }

    public static boolean verify(String secret, ReplayInfo info) {
        return ReplaySignatureCalculator.verify(secret, info.getReplayId(), info.getReplayMode().name(),
                String.valueOf(info.getTimestamp()), info.getNonce(), info.getSignature());
    }
}
