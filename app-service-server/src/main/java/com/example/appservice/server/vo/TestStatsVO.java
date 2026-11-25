package com.example.appservice.server.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestStatsVO {

    private long total;
    private long success;
    private long failed;
    private double successRate;
    private long avgCostMs;
    private long maxCostMs;
    private long minCostMs;
    private LocalDateTime lastTestAt;
}
