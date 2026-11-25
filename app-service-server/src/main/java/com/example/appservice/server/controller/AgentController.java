package com.example.appservice.server.controller;

import com.example.appservice.common.dto.ExternalCallBatchDTO;
import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.RegisterRequest;
import com.example.appservice.common.dto.RegisterResponse;
import com.example.appservice.common.dto.Result;
import com.example.appservice.common.dto.TrafficBatchDTO;
import com.example.appservice.server.entity.App;
import com.example.appservice.server.service.AppService;
import com.example.appservice.server.service.ExternalCallRecordService;
import com.example.appservice.server.service.TrafficRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AppService appService;
    private final TrafficRecordService trafficRecordService;
    private final ExternalCallRecordService externalCallRecordService;

    @PostMapping("/register")
    public Result<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return Result.ok(appService.register(request));
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody HeartbeatRequest request) {
        appService.heartbeat(request);
        return Result.<Void>ok(null);
    }

    @PostMapping("/traffic/records")
    public Result<Void> trafficRecords(@RequestBody TrafficBatchDTO request) {
        App app = appService.authenticate(request.getAppId(), request.getToken());
        trafficRecordService.saveBatch(app.getAppId(), request.getRecords());
        return Result.<Void>ok(null);
    }

    @PostMapping("/external-call/records")
    public Result<Void> externalCallRecords(@RequestBody ExternalCallBatchDTO request) {
        App app = appService.authenticate(request.getAppId(), request.getToken());
        externalCallRecordService.saveBatch(app.getAppId(), request.getRecords());
        return Result.<Void>ok(null);
    }
}
