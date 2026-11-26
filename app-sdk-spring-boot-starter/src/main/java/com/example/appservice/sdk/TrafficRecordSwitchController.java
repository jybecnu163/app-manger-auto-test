package com.example.appservice.sdk;

import com.example.appservice.common.dto.Result;
import com.example.appservice.common.dto.TrafficRecordSwitchRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * 接收管理端下发的录制开关指令，并校验 appId/token 与本地身份一致。
 */
@RestController
public class TrafficRecordSwitchController {

    private final AppIdentityResolver identity;
    private final TrafficRecordSwitch recorderSwitch;

    public TrafficRecordSwitchController(AppIdentityResolver identity, TrafficRecordSwitch recorderSwitch) {
        this.identity = identity;
        this.recorderSwitch = recorderSwitch;
    }

    /**
     * 更新本地录制开关状态。
     */
    @PostMapping("/api/agent/traffic-record/enabled")
    public Result<Void> set(@RequestBody TrafficRecordSwitchRequest request) {
        if (request == null || !StringUtils.hasText(request.getAppId())
                || !Objects.equals(request.getAppId(), identity.appId())
                || !Objects.equals(request.getToken(), identity.token())) {
            return Result.fail("invalid app identity");
        }
        recorderSwitch.set(Boolean.TRUE.equals(request.getEnabled()), request.getStartAt());
        return Result.ok(null);
    }
}
