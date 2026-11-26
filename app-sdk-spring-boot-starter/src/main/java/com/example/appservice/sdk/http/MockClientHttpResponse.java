package com.example.appservice.sdk.http;

import com.example.appservice.common.dto.MockResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 由 MockResponseDTO 构造的模拟 ClientHttpResponse。
 */
public class MockClientHttpResponse implements ClientHttpResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockResponseDTO rule;

    /**
     * 构造 MockClientHttpResponse 实例。
     */
    public MockClientHttpResponse(MockResponseDTO rule) {
        this.rule = rule;
    }

    /**
     * 返回模拟或缓存响应的状态码。
     */
    @Override
    public HttpStatusCode getStatusCode() {
        int status = rule.getMockStatus() == null ? 200 : rule.getMockStatus();
        return HttpStatusCode.valueOf(status);
    }

    /**
     * 返回模拟或缓存响应的状态文本。
     */
    @Override
    public String getStatusText() {
        return getStatusCode().toString();
    }

    /**
     * 关闭响应资源。
     */
    @Override
    public void close() {
    }

    /**
     * 返回可重复读取的响应体输入流。
     */
    @Override
    public InputStream getBody() {
        String body = rule.getMockBody() == null ? "" : rule.getMockBody();
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 返回模拟或缓存响应的头信息。
     */
    @Override
    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (rule.getMockHeaders() != null && !rule.getMockHeaders().isBlank()) {
            try {
                Map<?, ?> values = MAPPER.readValue(rule.getMockHeaders(), Map.class);
                for (Map.Entry<?, ?> entry : values.entrySet()) {
                    headers.add(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
                }
            } catch (Exception ignored) {
                // 非法 Mock Header 直接忽略，不影响 Mock 响应体
            }
        }
        return headers;
    }
}
