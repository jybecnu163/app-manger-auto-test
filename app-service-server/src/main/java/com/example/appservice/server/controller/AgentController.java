package com.example.appservice.server.controller;

import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.RegisterRequest;
import com.example.appservice.common.dto.RegisterResponse;
import com.example.appservice.common.dto.Result;
import com.example.appservice.server.service.AppService;
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

    @PostMapping("/register")
    public Result<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return Result.ok(appService.register(request));
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody HeartbeatRequest request) {
        appService.heartbeat(request);
        return Result.<Void>ok(null);
    }
}
