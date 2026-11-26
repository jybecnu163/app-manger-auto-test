package com.example.appservice.sdk.context;

import com.example.appservice.common.dto.ReplayMode;
import lombok.Data;

/**
 * 回放身份信息，承载 replayId、模式、时间戳、nonce 和签名。
 */
@Data
public class ReplayInfo {

    private String replayId;
    private ReplayMode replayMode;
    private long timestamp;
    private String nonce;
    private String signature;
}
