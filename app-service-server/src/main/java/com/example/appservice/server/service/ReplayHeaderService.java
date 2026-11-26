package com.example.appservice.server.service;

import com.example.appservice.common.util.ReplaySignatureCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 回放安全头生成服务，负责生成 replayId 与 HMAC 签名的 X-App-Replay-* 头。
 */
@Service
public class ReplayHeaderService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final String signatureSecret;

    public ReplayHeaderService(@Value("${app.replay.signature-secret:app-service-replay-secret}")
                               String signatureSecret) {
        this.signatureSecret = signatureSecret;
    }

    /**
     * 生成带时间戳和随机后缀的回放唯一标识。
     */
    public String newReplayId() {
        return "RP-" + LocalDateTime.now().format(FORMATTER) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    /**
     * 构造回放身份、模式、时间戳、nonce 与签名 Header。
     */
    public Map<String, String> buildHeaders(String replayId, long timestamp, String nonce) {
        String signature = ReplaySignatureCalculator.sign(signatureSecret, replayId, "DRY_RUN",
                String.valueOf(timestamp), nonce);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-App-Replay", "true");
        headers.put("X-App-Replay-Id", replayId);
        headers.put("X-App-Replay-Mode", "DRY_RUN");
        headers.put("X-App-Replay-Timestamp", String.valueOf(timestamp));
        headers.put("X-App-Replay-Nonce", nonce);
        headers.put("X-App-Replay-Signature", signature);
        return headers;
    }
}
