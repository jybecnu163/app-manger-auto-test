package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.appservice.common.dto.ReplayMode;
import com.example.appservice.server.entity.ApiInterface;
import com.example.appservice.server.entity.AppInstance;
import com.example.appservice.server.entity.TestLog;
import com.example.appservice.server.entity.TrafficRecord;
import com.example.appservice.server.mapper.ApiInterfaceMapper;
import com.example.appservice.server.mapper.TestLogMapper;
import com.example.appservice.server.mapper.TrafficRecordMapper;
import com.example.appservice.server.vo.BatchCaseResultVO;
import com.example.appservice.server.vo.BatchReplayResultVO;
import com.example.appservice.server.vo.TestCaseVO;
import com.example.appservice.server.vo.TestRequest;
import com.example.appservice.server.vo.TestResultVO;
import com.example.appservice.server.vo.TestStatsVO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TestProxyService {

    private static final int MAX_BODY = 1024 * 1024;

    private final ApiInterfaceMapper interfaceMapper;
    private final AppService appService;
    private final TestLogMapper testLogMapper;
    private final TestcaseService testcaseService;
    private final RestTemplate restTemplate;
    private final ReplayHeaderService replayHeaderService;
    private final TrafficRecordMapper trafficRecordMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestProxyService(ApiInterfaceMapper interfaceMapper,
                            AppService appService,
                            TestLogMapper testLogMapper,
                            TestcaseService testcaseService,
                            @Qualifier("proxyRestTemplate") RestTemplate restTemplate,
                            ReplayHeaderService replayHeaderService,
                            TrafficRecordMapper trafficRecordMapper) {
        this.interfaceMapper = interfaceMapper;
        this.appService = appService;
        this.testLogMapper = testLogMapper;
        this.testcaseService = testcaseService;
        this.restTemplate = restTemplate;
        this.replayHeaderService = replayHeaderService;
        this.trafficRecordMapper = trafficRecordMapper;
    }

    public TestResultVO test(String appId, TestRequest req) {
        ApiInterface api = interfaceMapper.selectById(req.getInterfaceId());
        if (api == null || !appId.equals(api.getAppId())) {
            throw new IllegalArgumentException("interface not found: " + req.getInterfaceId());
        }
        AppInstance instance = appService.findOnlineInstance(appId);
        if (instance == null) {
            throw new IllegalStateException("no online instance for app: " + appId);
        }

        boolean dryRun = "DRY_RUN".equalsIgnoreCase(req.getReplayMode());
        TrafficRecord recorded = null;
        if (dryRun && req.getTrafficRecordId() != null) {
            recorded = trafficRecordMapper.selectById(req.getTrafficRecordId());
            if (recorded == null || !appId.equals(recorded.getAppId())) {
                throw new IllegalArgumentException("traffic record not found: " + req.getTrafficRecordId());
            }
        }

        String targetPath = recorded == null ? api.getPath() : recorded.getPath();
        Map<String, String> queryParams = recorded == null
                ? req.getQueryParams() : parseMap(recorded.getQueryParams());
        Map<String, String> requestHeaders = recorded == null
                ? req.getHeaders() : parseMap(recorded.getHeaders());
        String requestBody = recorded == null ? req.getBody() : recorded.getBody();

        String url = "http://" + instance.getIp() + ":" + instance.getPort()
                + applyPathParams(targetPath, recorded == null ? req.getPathParams() : null)
                + buildQuery(queryParams);

        HttpHeaders headers = new HttpHeaders();
        if (requestHeaders != null) {
            requestHeaders.forEach(headers::set);
        }
        if (requestBody != null && !requestBody.isBlank()) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        String replayId = null;
        if (dryRun) {
            replayId = replayHeaderService.newReplayId();
            replayHeaderService.buildHeaders(replayId, System.currentTimeMillis(),
                    UUID.randomUUID().toString()).forEach(headers::set);
        }

        HttpMethod method = recorded != null && StringUtils.hasText(recorded.getMethod())
                ? HttpMethod.valueOf(recorded.getMethod().toUpperCase())
                : HttpMethod.valueOf(api.getMethod().toUpperCase());
        long start = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.exchange(
                url, method, new HttpEntity<>(requestBody, headers), String.class);
        long cost = System.currentTimeMillis() - start;

        String body = response.getBody() == null ? "" : response.getBody();
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY) + "\n...truncated";
        }

        TestResultVO vo = new TestResultVO();
        vo.setStatus(response.getStatusCode().value());
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.getHeaders().forEach((key, values) -> responseHeaders.put(key, String.join(",", values)));
        vo.setHeaders(responseHeaders);
        vo.setBody(body);
        vo.setCostMs(cost);
        if (dryRun) {
            vo.setReplayId(replayId);
            vo.setReplayMode(ReplayMode.DRY_RUN);
            vo.setDbRolledBack(Boolean.parseBoolean(responseHeader(response, "X-App-Replay-Rolled-Back")));
            vo.setMockHits(parseIntHeader(response, "X-App-Replay-Mock-Hits"));
            vo.setMockMisses(parseIntHeader(response, "X-App-Replay-Mock-Misses"));
        }

        TestLog log = new TestLog();
        log.setAppId(appId);
        log.setInterfaceId(api.getId());
        log.setRequestSummary(String.valueOf(req)
                + (dryRun ? " [DRY_RUN mockMisses=" + vo.getMockMisses() + "]" : ""));
        log.setResponseStatus(vo.getStatus());
        log.setCostMs(cost);
        log.setReplayId(vo.getReplayId());
        log.setReplayMode(vo.getReplayMode() == null ? "NONE" : vo.getReplayMode().name());
        log.setDbRolledBack(vo.isDbRolledBack());
        log.setMockHits(vo.getMockHits());
        log.setMockMisses(vo.getMockMisses());
        log.setCreatedAt(LocalDateTime.now());
        testLogMapper.insert(log);

        return vo;
    }

    public BatchReplayResultVO replayAll(String appId) {
        List<TestCaseVO> cases = testcaseService.listByApp(appId);
        BatchReplayResultVO result = new BatchReplayResultVO();
        result.setTotalCases(cases.size());

        List<BatchCaseResultVO> details = new ArrayList<>();
        long totalCost = 0;
        long maxCost = 0;
        long minCost = Long.MAX_VALUE;
        int executed = 0;
        int succeeded = 0;
        int failed = 0;
        int skipped = 0;

        for (TestCaseVO testCase : cases) {
            BatchCaseResultVO item = new BatchCaseResultVO();
            item.setCaseId(testCase.getId());
            item.setCaseName(testCase.getName());
            item.setInterfaceId(testCase.getInterfaceId());
            item.setInterfacePath(testCase.getInterfacePath());
            item.setInterfaceMethod(testCase.getInterfaceMethod());

            if (testCase.getInterfaceId() == null) {
                skipped++;
                item.setSuccess(false);
                item.setError("用例未关联接口");
                details.add(item);
                continue;
            }

            try {
                TestRequest request = new TestRequest();
                request.setInterfaceId(testCase.getInterfaceId());
                request.setPathParams(parseMap(testCase.getPathParams()));
                request.setQueryParams(parseMap(testCase.getQueryParams()));
                request.setHeaders(parseMap(testCase.getHeaders()));
                request.setBody(testCase.getBody());

                TestResultVO response = test(appId, request);
                executed++;
                boolean success = response.getStatus() >= 200 && response.getStatus() < 400;
                item.setStatus(response.getStatus());
                item.setCostMs(response.getCostMs());
                item.setSuccess(success);
                item.setBodyPreview(preview(response.getBody()));
                if (success) {
                    succeeded++;
                } else {
                    failed++;
                }
                long cost = Math.max(response.getCostMs(), 0);
                totalCost += cost;
                maxCost = Math.max(maxCost, cost);
                minCost = Math.min(minCost, cost);
            } catch (Exception e) {
                executed++;
                failed++;
                item.setSuccess(false);
                item.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            details.add(item);
        }

        result.setDetails(details);
        result.setExecuted(executed);
        result.setSucceeded(succeeded);
        result.setFailed(failed);
        result.setSkipped(skipped);
        result.setSuccessRate(executed == 0 ? 0 : Math.round(succeeded * 10000.0 / executed) / 100.0);
        result.setTotalCostMs(totalCost);
        result.setAvgCostMs(executed == 0 ? 0 : totalCost / executed);
        result.setMaxCostMs(maxCost);
        result.setMinCostMs(minCost == Long.MAX_VALUE ? 0 : minCost);
        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    private Map<String, String> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replaceAll("\\s+", " ");
        return text.length() > 120 ? text.substring(0, 120) + "..." : text;
    }

    public TestStatsVO stats(Long interfaceId, String replayMode) {
        String mode = replayMode == null || replayMode.isBlank() ? "NONE" : replayMode.trim().toUpperCase();
        List<TestLog> logs = testLogMapper.selectList(new LambdaQueryWrapper<TestLog>()
                .eq(TestLog::getInterfaceId, interfaceId)
                .eq(TestLog::getReplayMode, mode));
        TestStatsVO stats = new TestStatsVO();
        stats.setTotal(logs.size());
        if (logs.isEmpty()) {
            return stats;
        }

        long success = logs.stream().filter(log -> log.getResponseStatus() != null
                && log.getResponseStatus() >= 200 && log.getResponseStatus() < 400).count();
        long failed = logs.size() - success;
        long sum = 0;
        long max = 0;
        long min = Long.MAX_VALUE;
        LocalDateTime last = null;
        for (TestLog log : logs) {
            long cost = Math.max(log.getCostMs(), 0);
            sum += cost;
            max = Math.max(max, cost);
            min = Math.min(min, cost);
            if (log.getCreatedAt() != null && (last == null || log.getCreatedAt().isAfter(last))) {
                last = log.getCreatedAt();
            }
        }

        stats.setSuccess(success);
        stats.setFailed(failed);
        stats.setSuccessRate(Math.round(success * 10000.0 / logs.size()) / 100.0);
        stats.setAvgCostMs(sum / logs.size());
        stats.setMaxCostMs(max);
        stats.setMinCostMs(min == Long.MAX_VALUE ? 0 : min);
        stats.setLastTestAt(last);
        return stats;
    }

    private String responseHeader(ResponseEntity<String> response, String name) {
        java.util.List<String> values = response.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private int parseIntHeader(ResponseEntity<String> response, String name) {
        try {
            return Integer.parseInt(responseHeader(response, name));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String applyPathParams(String path, Map<String, String> pathParams) {
        if (pathParams == null || pathParams.isEmpty()) {
            return path;
        }
        String result = path;
        for (Map.Entry<String, String> entry : pathParams.entrySet()) {
            String value = URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8);
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }

    private String buildQuery(Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("?");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (builder.length() > 1) {
                builder.append('&');
            }
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }
}
