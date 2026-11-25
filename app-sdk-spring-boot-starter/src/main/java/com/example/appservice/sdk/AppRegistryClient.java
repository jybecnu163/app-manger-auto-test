package com.example.appservice.sdk;

import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.RegisterRequest;
import com.example.appservice.common.dto.RegisterResponse;
import com.example.appservice.common.dto.Result;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public class AppRegistryClient {

    private final String serverUrl;
    private final RestTemplate restTemplate;

    public AppRegistryClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public RegisterResponse register(RegisterRequest request) {
        Result<RegisterResponse> result = post("/api/agent/register", request,
                new ParameterizedTypeReference<Result<RegisterResponse>>() {
                });
        return unwrap(result);
    }

    public void heartbeat(HeartbeatRequest request) {
        Result<Void> result = post("/api/agent/heartbeat", request,
                new ParameterizedTypeReference<Result<Void>>() {
                });
        unwrap(result);
    }

    private <T> Result<T> post(String path, Object request, ParameterizedTypeReference<Result<T>> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Result<T>> response = restTemplate.exchange(
                serverUrl + path,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                responseType);
        return response.getBody();
    }

    private <T> T unwrap(Result<T> result) {
        if (result == null || result.getCode() != 0) {
            String message = result == null ? "空响应" : result.getMessage();
            throw new IllegalStateException("管理服务端返回失败：" + message);
        }
        return result.getData();
    }
}
