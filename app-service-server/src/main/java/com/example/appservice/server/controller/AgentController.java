package com.example.appservice.server.controller;

import com.example.appservice.common.dto.ExternalCallBatchDTO;
import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.HeartbeatResponse;
import com.example.appservice.common.dto.MockResponseDTO;
import com.example.appservice.common.dto.RegisterRequest;
import com.example.appservice.common.dto.RegisterResponse;
import com.example.appservice.common.dto.Result;
import com.example.appservice.common.dto.TrafficBatchDTO;
import com.example.appservice.server.entity.App;
import com.example.appservice.server.service.AppService;
import com.example.appservice.server.service.ExternalCallRecordService;
import com.example.appservice.server.service.MockResponseService;
import com.example.appservice.server.service.TrafficRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SDK Agent 上报入口，负责注册、心跳、流量/外部调用上报和 Mock 规则拉取。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AppService appService;
    private final TrafficRecordService trafficRecordService;
    private final ExternalCallRecordService externalCallRecordService;
    private final MockResponseService mockResponseService;

    /**
     * 处理业务实例注册请求，返回 appId 与 token。
     */
    @PostMapping("/register")
    public Result<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return Result.ok(appService.register(request));
    }

    /**
     * 处理心跳请求，返回实例状态与录制控制指令。
     */
    @PostMapping("/heartbeat")
    public Result<HeartbeatResponse> heartbeat(@RequestBody HeartbeatRequest request) {
        return Result.ok(appService.heartbeat(request));
    }

    /**
     * 鉴权后批量接收入站流量记录。
     */
    @PostMapping("/traffic/records")
    public Result<Void> trafficRecords(@RequestBody TrafficBatchDTO request) {
        App app = appService.authenticate(request.getAppId(), request.getToken());
        trafficRecordService.saveBatch(app.getAppId(), request.getRecords());
        return Result.<Void>ok(null);
    }

    /**
     * 鉴权后批量接收出站 HTTP 调用记录。
     */
    @PostMapping("/external-call/records")
    public Result<Void> externalCallRecords(@RequestBody ExternalCallBatchDTO request) {
        App app = appService.authenticate(request.getAppId(), request.getToken());
        externalCallRecordService.saveBatch(app.getAppId(), request.getRecords());
        return Result.<Void>ok(null);
    }

    /**
     * 鉴权后按 appId 返回启用的 Mock 规则。
     */
    @GetMapping("/mock-responses")
    public Result<List<MockResponseDTO>> mockResponses(@RequestParam String appId,
                                                       @RequestHeader("X-App-Token") String token) {
        App app = appService.authenticate(appId, token);
        return Result.ok(mockResponseService.listByApp(app.getAppId()));
    }
}
