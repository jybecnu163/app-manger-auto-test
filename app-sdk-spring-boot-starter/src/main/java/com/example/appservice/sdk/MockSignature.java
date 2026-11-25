package com.example.appservice.sdk;

import com.example.appservice.common.util.RequestHashUtil;

public final class MockSignature {

    private MockSignature() {
    }

    public static String of(String method, String url, String maskedBody) {
        return RequestHashUtil.of(method, url, null, maskedBody);
    }
}
