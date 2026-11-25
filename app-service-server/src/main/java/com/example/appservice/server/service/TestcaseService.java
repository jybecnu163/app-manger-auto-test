package com.example.appservice.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.appservice.server.entity.ApiInterface;
import com.example.appservice.server.entity.ApiTestCase;
import com.example.appservice.server.mapper.ApiInterfaceMapper;
import com.example.appservice.server.mapper.ApiTestCaseMapper;
import com.example.appservice.server.vo.TestCaseSaveRequest;
import com.example.appservice.server.vo.TestCaseVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TestcaseService {

    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final ApiTestCaseMapper testCaseMapper;
    private final ApiInterfaceMapper interfaceMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TestcaseService(ApiTestCaseMapper testCaseMapper, ApiInterfaceMapper interfaceMapper) {
        this.testCaseMapper = testCaseMapper;
        this.interfaceMapper = interfaceMapper;
    }

    public List<TestCaseVO> list(Long interfaceId) {
        List<TestCaseVO> result = new ArrayList<>();
        List<ApiTestCase> cases = testCaseMapper.selectList(new LambdaQueryWrapper<ApiTestCase>()
                .eq(ApiTestCase::getInterfaceId, interfaceId)
                .orderByAsc(ApiTestCase::getSource)
                .orderByAsc(ApiTestCase::getId));
        for (ApiTestCase testCase : cases) {
            result.add(toVO(testCase));
        }
        return result;
    }

    public List<TestCaseVO> listByApp(String appId) {
        Map<Long, ApiInterface> interfaces = new HashMap<>();
        for (ApiInterface api : interfaceMapper.selectList(new LambdaQueryWrapper<ApiInterface>()
                .eq(ApiInterface::getAppId, appId))) {
            interfaces.put(api.getId(), api);
        }

        List<TestCaseVO> result = new ArrayList<>();
        List<ApiTestCase> cases = testCaseMapper.selectList(new LambdaQueryWrapper<ApiTestCase>()
                .eq(ApiTestCase::getAppId, appId)
                .orderByAsc(ApiTestCase::getInterfaceId)
                .orderByAsc(ApiTestCase::getId));
        for (ApiTestCase testCase : cases) {
            result.add(toVO(testCase, interfaces.get(testCase.getInterfaceId())));
        }
        return result;
    }

    public TestCaseVO save(String appId, Long interfaceId, TestCaseSaveRequest request, String source) {
        String name = StringUtils.hasText(request.getName())
                ? request.getName()
                : "测试-" + LocalDateTime.now().format(NAME_TIME);
        return saveInternal(appId, interfaceId, name, request.getPathParams(), request.getQueryParams(),
                request.getHeaders(), request.getBody(), source);
    }

    public void delete(Long interfaceId, Long caseId) {
        testCaseMapper.delete(new LambdaQueryWrapper<ApiTestCase>()
                .eq(ApiTestCase::getInterfaceId, interfaceId)
                .eq(ApiTestCase::getId, caseId));
    }

    public void deleteByAppId(String appId) {
        testCaseMapper.delete(new LambdaQueryWrapper<ApiTestCase>().eq(ApiTestCase::getAppId, appId));
    }

    public void generateAutoCases(String appId, ApiInterface api) {
        JsonNode schema;
        try {
            schema = objectMapper.readTree(api.getRequestSchema() == null ? "{}" : api.getRequestSchema());
        } catch (Exception e) {
            schema = objectMapper.createObjectNode();
        }

        Map<String, String> defaultPath = paramsOf(schema.path("pathParams"), false);
        Map<String, String> defaultQuery = paramsOf(schema.path("queryParams"), false);
        Map<String, String> defaultHeaders = paramsOf(schema.path("headers"), false);
        String defaultBody = bodyOf(schema.path("body"), false);

        Map<String, String> empty = new LinkedHashMap<>();
        saveInternal(appId, api.getId(), "默认用例", defaultPath, defaultQuery, defaultHeaders, defaultBody, "AUTO");
        saveInternal(appId, api.getId(), "空请求用例", empty, empty, empty, "{}", "AUTO");
        saveInternal(appId, api.getId(), "备选值用例",
                paramsOf(schema.path("pathParams"), true),
                paramsOf(schema.path("queryParams"), true),
                paramsOf(schema.path("headers"), true),
                bodyOf(schema.path("body"), true),
                "AUTO");
    }

    private synchronized TestCaseVO saveInternal(String appId, Long interfaceId, String name,
                                                 Map<String, String> pathParams,
                                                 Map<String, String> queryParams,
                                                 Map<String, String> headers,
                                                 String body,
                                                 String source) {
        Map<String, String> safePath = pathParams == null ? new LinkedHashMap<>() : pathParams;
        Map<String, String> safeQuery = queryParams == null ? new LinkedHashMap<>() : queryParams;
        Map<String, String> safeHeaders = headers == null ? new LinkedHashMap<>() : headers;
        String safeBody = body == null || body.isBlank() ? "{}" : body;

        String hash = hash(interfaceId, safePath, safeQuery, safeHeaders, safeBody);
        ApiTestCase existing = testCaseMapper.selectOne(new LambdaQueryWrapper<ApiTestCase>()
                .eq(ApiTestCase::getInterfaceId, interfaceId)
                .eq(ApiTestCase::getContentHash, hash)
                .last("limit 1"));
        if (existing != null) {
            return toVO(existing);
        }

        ApiTestCase testCase = new ApiTestCase();
        testCase.setAppId(appId);
        testCase.setInterfaceId(interfaceId);
        testCase.setName(name);
        testCase.setPathParams(toJson(safePath));
        testCase.setQueryParams(toJson(safeQuery));
        testCase.setHeaders(toJson(safeHeaders));
        testCase.setBody(safeBody);
        testCase.setContentHash(hash);
        testCase.setSource(source);
        testCase.setCreatedAt(LocalDateTime.now());
        testCaseMapper.insert(testCase);
        return toVO(testCase);
    }

    private String hash(Long interfaceId, Map<String, String> pathParams, Map<String, String> queryParams,
                        Map<String, String> headers, String body) {
        String content = interfaceId + "|" + toJson(pathParams) + "|" + toJson(queryParams)
                + "|" + toJson(headers) + "|" + body;
        return DigestUtils.md5DigestAsHex(content.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> paramsOf(JsonNode group, boolean variant) {
        Map<String, String> result = new LinkedHashMap<>();
        if (group == null || !group.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = group.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), sampleValue(field.getValue(), variant));
        }
        return result;
    }

    private String bodyOf(JsonNode body, boolean variant) {
        if (body == null || body.isMissingNode()) {
            return "{}";
        }
        if (body.isObject() && body.has("properties")) {
            return sampleValue(body, variant);
        }
        return sampleValue(body, variant);
    }

    private String sampleValue(JsonNode schema, boolean variant) {
        if (schema == null || schema.isMissingNode()) {
            return "test";
        }
        if (schema.isTextual()) {
            switch (schema.asText()) {
                case "integer":
                    return variant ? "2" : "1";
                case "number":
                    return variant ? "2.0" : "1.0";
                case "boolean":
                    return variant ? "false" : "true";
                case "date-time":
                    return variant ? "2026-12-31T00:00:00" : "2026-01-01T00:00:00";
                default:
                    return variant ? "demo" : "test";
            }
        }
        if (schema.isObject()) {
            if ("enum".equals(schema.path("type").asText())) {
                JsonNode values = schema.path("values");
                if (values.isArray() && values.size() > 0) {
                    return values.get(variant ? values.size() - 1 : 0).asText();
                }
                return "DRAFT";
            }
            if ("array".equals(schema.path("type").asText())) {
                List<String> items = new ArrayList<>();
                items.add(sampleValue(schema.path("items"), variant));
                return toJson(items);
            }
            if (schema.has("properties")) {
                Map<String, String> object = new LinkedHashMap<>();
                Iterator<Map.Entry<String, JsonNode>> fields = schema.path("properties").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    object.put(field.getKey(), sampleValue(field.getValue(), variant));
                }
                return toJson(object);
            }
            if (schema.path("additionalProperties").isObject()) {
                return "{}";
            }
        }
        return "test";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private TestCaseVO toVO(ApiTestCase testCase) {
        return toVO(testCase, interfaceMapper.selectById(testCase.getInterfaceId()));
    }

    private TestCaseVO toVO(ApiTestCase testCase, ApiInterface api) {
        TestCaseVO vo = new TestCaseVO();
        vo.setId(testCase.getId());
        vo.setInterfaceId(testCase.getInterfaceId());
        if (api != null) {
            vo.setInterfaceName(api.getOperation());
            vo.setInterfaceMethod(api.getMethod());
            vo.setInterfacePath(api.getPath());
        }
        vo.setName(testCase.getName());
        vo.setPathParams(testCase.getPathParams());
        vo.setQueryParams(testCase.getQueryParams());
        vo.setHeaders(testCase.getHeaders());
        vo.setBody(testCase.getBody());
        vo.setSource(testCase.getSource());
        return vo;
    }
}
