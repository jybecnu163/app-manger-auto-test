package com.example.appservice.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ExternalCallBatchDTO {

    private String appId;
    private String token;
    private List<ExternalCallRecordDTO> records = new ArrayList<>();
}
