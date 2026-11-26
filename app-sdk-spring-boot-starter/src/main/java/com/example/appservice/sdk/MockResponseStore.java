package com.example.appservice.sdk;

import com.example.appservice.common.dto.MockResponseDTO;
import com.example.appservice.common.dto.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按 replayId 缓存 Mock 规则，并提供匹配与命中统计。
 */
public class MockResponseStore {

    private static final Logger log = LoggerFactory.getLogger(MockResponseStore.class);

    private final AppRegistryProperties properties;
    private final AppIdentityResolver identity;
    private final RestTemplate restTemplate;
    private final Map<String, MockResponseDTO> rules = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicInteger misses = new AtomicInteger();

    private volatile String loadedForReplayId;

    public MockResponseStore(AppRegistryProperties properties, AppIdentityResolver identity) {
        this.properties = properties;
        this.identity = identity;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 按 replayId 从管理端加载 Mock 规则。
     */
    public synchronized void load(String replayId) {
        String appId = identity.appId();
        if (!StringUtils.hasText(appId)) {
            return;
        }
        if (replayId != null && replayId.equals(loadedForReplayId) && !rules.isEmpty()) {
            return;
        }
        reset();
        loadedForReplayId = replayId;
        try {
            String path = properties.getReplay().getMockResponsePath();
            String url = trimTrailingSlash(properties.getServerUrl()) + path
                    + "?appId=" + URLEncoder.encode(appId, StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-App-Token", identity.token() == null ? "" : identity.token());
            ResponseEntity<Result<List<MockResponseDTO>>> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), new ParameterizedTypeReference<Result<List<MockResponseDTO>>>() {
                    });
            Result<List<MockResponseDTO>> payload = response.getBody();
            List<MockResponseDTO> body = payload == null ? null : payload.getData();
            if (body != null) {
                for (MockResponseDTO rule : body) {
                    if (rule != null && rule.isEnabled() && StringUtils.hasText(rule.getRequestHash())) {
                        rules.put(rule.getRequestHash(), rule);
                    }
                }
            }
            log.info("已加载 Mock 规则 {} 条，replayId={}", rules.size(), replayId);
        } catch (Exception e) {
            log.warn("加载 Mock 规则失败，replayId={}：{}", replayId, e.getMessage());
        }
    }

    /**
     * 按 requestHash 查找启用的 Mock 规则。
     */
    public MockResponseDTO find(String requestHash) {
        MockResponseDTO rule = requestHash == null ? null : rules.get(requestHash);
        if (rule == null) {
            misses.incrementAndGet();
        } else {
            hits.incrementAndGet();
        }
        return rule;
    }

    /**
     * 返回 Mock 命中次数。
     */
    public int hits() {
        return hits.get();
    }

    /**
     * 返回 Mock 未命中次数。
     */
    public int misses() {
        return misses.get();
    }

    /**
     * 重置命中与未命中计数。
     */
    public void reset() {
        rules.clear();
        hits.set(0);
        misses.set(0);
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
