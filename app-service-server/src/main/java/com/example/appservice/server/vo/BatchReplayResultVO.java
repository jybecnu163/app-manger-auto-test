package com.example.appservice.server.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class BatchReplayResultVO {

    private int totalCases;
    private int executed;
    private int succeeded;
    private int failed;
    private int skipped;
    private double successRate;
    private long totalCostMs;
    private long avgCostMs;
    private long maxCostMs;
    private long minCostMs;
    private LocalDateTime finishedAt;
    private List<BatchCaseResultVO> details = new ArrayList<>();
}
