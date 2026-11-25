package com.example.appservice.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrafficBatchDTO {

    private String appId;
    private String token;
    private List<TrafficRecordDTO> records = new ArrayList<>();
}
