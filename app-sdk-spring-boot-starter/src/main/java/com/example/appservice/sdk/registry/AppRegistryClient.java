package com.example.appservice.sdk.registry;

import com.example.appservice.common.dto.HeartbeatRequest;
import com.example.appservice.common.dto.HeartbeatResponse;
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

/**
 * 管理端 HTTP 客户端，负责注册和心跳请求。
 */
public class AppRegistryClient {

    private final String serverUrl;
    private final RestTemplate restTemplate;

    /**
     * 构造 AppRegistryClient 实例。
     */
    public AppRegistryClient(String serverUrl) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 调用 /api/agent/register 完成实例注册。
     */
    public RegisterResponse register(RegisterRequest request) {
        Result<RegisterResponse> result = post("/api/agent/register", request,
                new ParameterizedTypeReference<Result<RegisterResponse>>() {
                });
        return unwrap(result);
    }

    /**
     * 调用 /api/agent/heartbeat 上报心跳并接收录制控制响应。
     */
    public HeartbeatResponse heartbeat(HeartbeatRequest request) {
        Result<HeartbeatResponse> result = post("/api/agent/heartbeat", request,
                new ParameterizedTypeReference<Result<HeartbeatResponse>>() {
                });
        return unwrap(result);
    }

    /**
     * 执行 post 相关逻辑。
     */
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

    /**
     * 执行 unwrap 相关逻辑。
     */
    private <T> T unwrap(Result<T> result) {
        if (result == null || result.getCode() != 0) {
            String message = result == null ? "空响应" : result.getMessage();
            throw new IllegalStateException("管理服务端返回失败：" + message);
        }
        return result.getData();
    }
}
