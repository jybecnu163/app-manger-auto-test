package com.example.appservice.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ReplaySignatureCalculator {

    private static final String ALGORITHM = "HmacSHA256";

    private ReplaySignatureCalculator() {
    }

    public static String sign(String secret, String replayId, String mode,
                              String timestamp, String nonce) {
        String content = nullToEmpty(replayId) + "|" + nullToEmpty(mode) + "|"
                + nullToEmpty(timestamp) + "|" + nullToEmpty(nonce);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(nullToEmpty(secret).getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法计算回放签名", e);
        }
    }

    public static boolean verify(String secret, String replayId, String mode,
                                 String timestamp, String nonce, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(secret, replayId, mode, timestamp, nonce);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
