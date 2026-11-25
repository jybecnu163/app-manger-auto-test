package com.example.appservice.sdk;

import com.example.appservice.common.dto.ReplayMode;
import lombok.Data;

@Data
public class ReplayInfo {

    private String replayId;
    private ReplayMode replayMode;
    private long timestamp;
    private String nonce;
    private String signature;
}
