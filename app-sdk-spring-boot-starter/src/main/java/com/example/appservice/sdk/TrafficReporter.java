package com.example.appservice.sdk;

import com.example.appservice.common.dto.ExternalCallBatchDTO;
import com.example.appservice.common.dto.TrafficBatchDTO;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrafficReporter {

    private static final Logger log = LoggerFactory.getLogger(TrafficReporter.class);
    private static final int MAX_RETRIES = 2;

    private final AppRegistryProperties properties;
    private final ObjectProvider<TrafficRecordBuffer> trafficBuffer;
    private final ObjectProvider<ExternalCallRecordBuffer> externalCallBuffer;
    private final AppIdentityResolver identity;
    private final RestTemplate restTemplate;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public TrafficReporter(AppRegistryProperties properties,
                           ObjectProvider<TrafficRecordBuffer> trafficBuffer,
                           ObjectProvider<ExternalCallRecordBuffer> externalCallBuffer,
                           AppIdentityResolver identity) {
        this.properties = properties;
        this.trafficBuffer = trafficBuffer;
        this.externalCallBuffer = externalCallBuffer;
        this.identity = identity;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Scheduled(initialDelayString = "#{@appRegistryProperties.trafficRecord.flushInterval.toMillis()}",
            fixedDelayString = "#{@appRegistryProperties.trafficRecord.flushInterval.toMillis()}")
    public void flush() {
        if (!running.get()) {
            return;
        }
        flushTrafficRecords();
        flushExternalCallRecords();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
    }

    private void flushTrafficRecords() {
        if (!properties.getTrafficRecord().isEnabled()) {
            return;
        }
        TrafficRecordBuffer buffer = trafficBuffer.getIfAvailable();
        if (buffer == null) {
            return;
        }
        List<?> records = buffer.drain(properties.getTrafficRecord().getBatchSize());
        if (records.isEmpty()) {
            return;
        }
        TrafficBatchDTO batch = new TrafficBatchDTO();
        batch.setAppId(identity.appId());
        batch.setToken(identity.token());
        batch.setRecords(cast(records));
        if (!post("/api/agent/traffic/records", batch)) {
            log.warn("入站流量上报失败，丢弃 {} 条记录", records.size());
        }
    }

    private void flushExternalCallRecords() {
        if (!properties.getExternalCallRecord().isEnabled()) {
            return;
        }
        ExternalCallRecordBuffer buffer = externalCallBuffer.getIfAvailable();
        if (buffer == null) {
            return;
        }
        List<?> records = buffer.drain(properties.getTrafficRecord().getBatchSize());
        if (records.isEmpty()) {
            return;
        }
        ExternalCallBatchDTO batch = new ExternalCallBatchDTO();
        batch.setAppId(identity.appId());
        batch.setToken(identity.token());
        batch.setRecords(cast(records));
        if (!post("/api/agent/external-call/records", batch)) {
            log.warn("出站调用上报失败，丢弃 {} 条记录", records.size());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cast(List<?> records) {
        return (List<T>) records;
    }

    private boolean post(String path, Object payload) {
        String appId = identity.appId();
        String token = identity.token();
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(token)) {
            log.warn("缺少 appId/token，跳过流量上报");
            return false;
        }
        String url = properties.getServerUrl();
        if (!StringUtils.hasText(url)) {
            return false;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        trimTrailingSlash(url) + path, new HttpEntity<>(payload, headers), String.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                }
                log.warn("流量上报返回异常状态 {}，第 {} 次尝试", response.getStatusCode(), attempt + 1);
            } catch (Exception e) {
                log.warn("流量上报失败，第 {} 次尝试：{}", attempt + 1, e.getMessage());
            }
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(100L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
