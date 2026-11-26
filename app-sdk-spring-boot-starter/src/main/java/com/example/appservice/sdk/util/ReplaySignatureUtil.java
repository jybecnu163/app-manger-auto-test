package com.example.appservice.sdk.util;
import com.example.appservice.sdk.context.ReplayInfo;

import com.example.appservice.common.util.ReplaySignatureCalculator;

/**
 * 回放签名工具，委托共享模块完成回放请求签名与验签。
 */
public final class ReplaySignatureUtil {

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private ReplaySignatureUtil() {
    }

    /**
     * 生成回放签名或 Mock 请求指纹。
     */
    public static String sign(String secret, ReplayInfo info) {
        return ReplaySignatureCalculator.sign(secret, info.getReplayId(), info.getReplayMode().name(),
                String.valueOf(info.getTimestamp()), info.getNonce());
    }

    /**
     * 校验回放签名是否有效。
     */
    public static boolean verify(String secret, ReplayInfo info) {
        return ReplaySignatureCalculator.verify(secret, info.getReplayId(), info.getReplayMode().name(),
                String.valueOf(info.getTimestamp()), info.getNonce(), info.getSignature());
    }
}
