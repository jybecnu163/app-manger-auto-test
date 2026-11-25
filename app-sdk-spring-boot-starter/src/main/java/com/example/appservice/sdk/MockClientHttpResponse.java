package com.example.appservice.sdk;

import com.example.appservice.common.dto.MockResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MockClientHttpResponse implements ClientHttpResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MockResponseDTO rule;

    public MockClientHttpResponse(MockResponseDTO rule) {
        this.rule = rule;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        int status = rule.getMockStatus() == null ? 200 : rule.getMockStatus();
        return HttpStatusCode.valueOf(status);
    }

    @Override
    public String getStatusText() {
        return getStatusCode().toString();
    }

    @Override
    public void close() {
    }

    @Override
    public InputStream getBody() {
        String body = rule.getMockBody() == null ? "" : rule.getMockBody();
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

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
