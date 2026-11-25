package com.example.appservice.server.vo;

import com.example.appservice.common.dto.ReplayMode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class TestResultVO {

    private int status;
    private Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private long costMs;
    private String replayId;
    private ReplayMode replayMode = ReplayMode.NONE;
    private boolean dbRolledBack;
    private int mockHits;
    private int mockMisses;
}
