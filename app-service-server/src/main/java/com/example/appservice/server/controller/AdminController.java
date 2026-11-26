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

/**
 * 管理端 REST 接口聚合，提供应用、接口、用例、流量、Mock、录制开关和测试回放能力。
 */
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

    /**
     * 查询应用列表。
     */
    @GetMapping("/apps")
    public Result<List<AppListItemVO>> apps() {
        return Result.ok(appService.listApps());
    }

    /**
     * 查询应用详情。
     */
    @GetMapping("/apps/{appId}")
    public Result<AppDetailVO> detail(@PathVariable String appId) {
        return Result.ok(appService.detail(appId));
    }

    /**
     * 查询应用已上报的接口列表。
     */
    @GetMapping("/apps/{appId}/interfaces")
    public Result<List<ApiInterface>> interfaces(@PathVariable String appId) {
        return Result.ok(appService.listInterfaces(appId));
    }

    /**
     * 执行单接口测试或 Dry-Run 回放。
     */
    @PostMapping("/apps/{appId}/test")
    public Result<TestResultVO> test(@PathVariable String appId, @RequestBody TestRequest request) {
        return Result.ok(testProxyService.test(appId, request));
    }

    /**
     * 按接口查询测试用例。
     */
    @GetMapping("/apps/{appId}/interfaces/{interfaceId}/cases")
    public Result<List<TestCaseVO>> cases(@PathVariable String appId, @PathVariable Long interfaceId) {
        return Result.ok(testcaseService.list(interfaceId));
    }

    /**
     * 查询应用下全部测试用例。
     */
    @GetMapping("/apps/{appId}/cases")
    public Result<List<TestCaseVO>> appCases(@PathVariable String appId) {
        return Result.ok(testcaseService.listByApp(appId));
    }

    /**
     * 批量回放应用下全部用例。
     */
    @PostMapping("/apps/{appId}/cases/replay-all")
    public Result<BatchReplayResultVO> replayAllCases(@PathVariable String appId) {
        return Result.ok(testProxyService.replayAll(appId));
    }

    /**
     * 查询接口测试统计。
     */
    @GetMapping("/interfaces/{interfaceId}/stats")
    public Result<TestStatsVO> stats(@PathVariable Long interfaceId,
                                     @RequestParam(required = false) String replayMode) {
        return Result.ok(testProxyService.stats(interfaceId, replayMode));
    }

    /**
     * 保存用户自定义测试用例。
     */
    @PostMapping("/apps/{appId}/interfaces/{interfaceId}/cases")
    public Result<TestCaseVO> saveCase(@PathVariable String appId,
                                       @PathVariable Long interfaceId,
                                       @RequestBody TestCaseSaveRequest request) {
        return Result.ok(testcaseService.save(appId, interfaceId, request, "USER"));
    }

    /**
     * 删除测试用例。
     */
    @DeleteMapping("/apps/{appId}/interfaces/{interfaceId}/cases/{caseId}")
    public Result<Void> deleteCase(@PathVariable String appId,
                                   @PathVariable Long interfaceId,
                                   @PathVariable Long caseId) {
        testcaseService.delete(interfaceId, caseId);
        return Result.<Void>ok(null);
    }

    /**
     * 按应用和接口查询 Mock 规则。
     */
    @GetMapping("/interfaces/{interfaceId}/mock-responses")
    public Result<List<MockResponseDTO>> mockResponses(@PathVariable Long interfaceId,
                                                       @RequestParam(required = false) String appId) {
        return Result.ok(mockResponseService.listByInterface(appId, interfaceId));
    }

    /**
     * 新建或由外部调用记录生成 Mock 规则。
     */
    @PostMapping("/mock-responses")
    public Result<MockResponseDTO> saveMockResponse(@RequestBody MockResponseSaveRequest request) {
        return Result.ok(mockResponseService.create(request));
    }

    /**
     * 启用或禁用 Mock 规则。
     */
    @PostMapping("/mock-responses/{id}/enabled")
    public Result<Void> setMockEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        mockResponseService.setEnabled(id, enabled);
        return Result.<Void>ok(null);
    }

    /**
     * 设置应用录制开关，并可指定录制生效时间。
     */
    @PostMapping("/apps/{appId}/traffic-record/enabled")
    public Result<Void> setTrafficRecordEnabled(@PathVariable String appId,
                                                @RequestParam boolean enabled,
                                                @RequestBody(required = false) TrafficRecordSwitchRequest request) {
        recordingControlService.setEnabled(appId, enabled,
                request == null ? null : request.getStartAt());
        return Result.<Void>ok(null);
    }

    /**
     * 按多条件查询已录制的入站流量。
     */
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

    /**
     * 查询应用出站 HTTP 调用记录。
     */
    @GetMapping("/apps/{appId}/external-call/records")
    public Result<List<ExternalCallRecord>> externalCallRecords(@PathVariable String appId) {
        return Result.ok(externalCallRecordService.listByApp(appId));
    }

    /**
     * 兼容本地时间与带偏移时间字符串的解析。
     */
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
