package com.example.appservice.server.controller;

import com.example.appservice.common.dto.MockResponseDTO;
import com.example.appservice.common.dto.Result;
import com.example.appservice.common.dto.TrafficRecordSwitchRequest;
import com.example.appservice.server.entity.ApiInterface;
import com.example.appservice.server.entity.ExternalCallRecord;
import com.example.appservice.server.entity.TrafficRecord;
import com.example.appservice.server.service.AppService;
import com.example.appservice.server.service.ExternalCallRecordService;
import com.example.appservice.server.service.MockResponseService;
import com.example.appservice.server.service.RecordingControlService;
import com.example.appservice.server.service.TrafficRecordService;
import com.example.appservice.server.service.TestProxyService;
import com.example.appservice.server.service.TestcaseService;
import com.example.appservice.server.vo.AppDetailVO;
import com.example.appservice.server.vo.AppListItemVO;
import com.example.appservice.server.vo.BatchReplayResultVO;
import com.example.appservice.server.vo.MockResponseSaveRequest;
import com.example.appservice.server.vo.TestCaseSaveRequest;
import com.example.appservice.server.vo.TestCaseVO;
import com.example.appservice.server.vo.TestRequest;
import com.example.appservice.server.vo.TestResultVO;
import com.example.appservice.server.vo.TestStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AppService appService;
    private final TestProxyService testProxyService;
    private final TestcaseService testcaseService;
    private final MockResponseService mockResponseService;
    private final TrafficRecordService trafficRecordService;
    private final ExternalCallRecordService externalCallRecordService;
    private final RecordingControlService recordingControlService;

    @GetMapping("/apps")
    public Result<List<AppListItemVO>> apps() {
        return Result.ok(appService.listApps());
    }

    @GetMapping("/apps/{appId}")
    public Result<AppDetailVO> detail(@PathVariable String appId) {
        return Result.ok(appService.detail(appId));
    }

    @GetMapping("/apps/{appId}/interfaces")
    public Result<List<ApiInterface>> interfaces(@PathVariable String appId) {
        return Result.ok(appService.listInterfaces(appId));
    }

    @PostMapping("/apps/{appId}/test")
    public Result<TestResultVO> test(@PathVariable String appId, @RequestBody TestRequest request) {
        return Result.ok(testProxyService.test(appId, request));
    }

    @GetMapping("/apps/{appId}/interfaces/{interfaceId}/cases")
    public Result<List<TestCaseVO>> cases(@PathVariable String appId, @PathVariable Long interfaceId) {
        return Result.ok(testcaseService.list(interfaceId));
    }

    @GetMapping("/apps/{appId}/cases")
    public Result<List<TestCaseVO>> appCases(@PathVariable String appId) {
        return Result.ok(testcaseService.listByApp(appId));
    }

    @PostMapping("/apps/{appId}/cases/replay-all")
    public Result<BatchReplayResultVO> replayAllCases(@PathVariable String appId) {
        return Result.ok(testProxyService.replayAll(appId));
    }

    @GetMapping("/interfaces/{interfaceId}/stats")
    public Result<TestStatsVO> stats(@PathVariable Long interfaceId,
                                     @RequestParam(required = false) String replayMode) {
        return Result.ok(testProxyService.stats(interfaceId, replayMode));
    }

    @PostMapping("/apps/{appId}/interfaces/{interfaceId}/cases")
    public Result<TestCaseVO> saveCase(@PathVariable String appId,
                                       @PathVariable Long interfaceId,
                                       @RequestBody TestCaseSaveRequest request) {
        return Result.ok(testcaseService.save(appId, interfaceId, request, "USER"));
    }

    @DeleteMapping("/apps/{appId}/interfaces/{interfaceId}/cases/{caseId}")
    public Result<Void> deleteCase(@PathVariable String appId,
                                   @PathVariable Long interfaceId,
                                   @PathVariable Long caseId) {
        testcaseService.delete(interfaceId, caseId);
        return Result.<Void>ok(null);
    }

    @GetMapping("/interfaces/{interfaceId}/mock-responses")
    public Result<List<MockResponseDTO>> mockResponses(@PathVariable Long interfaceId,
                                                       @RequestParam(required = false) String appId) {
        return Result.ok(mockResponseService.listByInterface(appId, interfaceId));
    }

    @PostMapping("/mock-responses")
    public Result<MockResponseDTO> saveMockResponse(@RequestBody MockResponseSaveRequest request) {
        return Result.ok(mockResponseService.create(request));
    }

    @PostMapping("/mock-responses/{id}/enabled")
    public Result<Void> setMockEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        mockResponseService.setEnabled(id, enabled);
        return Result.<Void>ok(null);
    }

    @PostMapping("/apps/{appId}/traffic-record/enabled")
    public Result<Void> setTrafficRecordEnabled(@PathVariable String appId,
                                                @RequestParam boolean enabled,
                                                @RequestBody(required = false) TrafficRecordSwitchRequest request) {
        recordingControlService.setEnabled(appId, enabled,
                request == null ? null : request.getStartAt());
        return Result.<Void>ok(null);
    }

    @GetMapping("/apps/{appId}/traffic/records")
    public Result<List<TrafficRecord>> trafficRecords(
            @PathVariable String appId,
            @RequestParam(required = false) Long interfaceId,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "false") boolean onlyFailures,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String traceId) {
        return Result.ok(trafficRecordService.list(appId, interfaceId, method, status, onlyFailures,
                parseDateTime(startTime), parseDateTime(endTime), traceId));
    }

    @GetMapping("/apps/{appId}/external-call/records")
    public Result<List<ExternalCallRecord>> externalCallRecords(@PathVariable String appId) {
        return Result.ok(externalCallRecordService.listByApp(appId));
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value).toLocalDateTime();
        }
    }
}
