package com.example.appservice.sdk.util;

import com.example.appservice.common.util.RequestHashUtil;

/**
 * 出站 Mock 请求指纹工具，口径与录制侧 RequestHashUtil 保持一致。
 */
public final class MockSignature {

    /**
     * 私有构造函数，防止工具类被实例化。
     */
    private MockSignature() {
    }

    /**
     * 计算请求指纹或签名输入。
     */
    public static String of(String method, String url, String maskedBody) {
        return RequestHashUtil.of(method, url, null, maskedBody);
    }
}
