package com.example.appservice.sdk;

import com.example.appservice.common.dto.ExternalCallRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

public class OutboundHttpRecordingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OutboundHttpRecordingInterceptor.class);
    private static final long MAX_CAPTURE_BYTES = 2 * 1024 * 1024L;

    private final AppRegistryProperties properties;
    private final ExternalCallRecordBuffer buffer;
    private final AppIdentityResolver identity;

    public OutboundHttpRecordingInterceptor(AppRegistryProperties properties,
                                            ExternalCallRecordBuffer buffer,
                                            AppIdentityResolver identity) {
        this.properties = properties;
        this.buffer = buffer;
        this.identity = identity;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        AppRegistryProperties.ExternalCallRecordProperties config = properties.getExternalCallRecord();
        if (!config.isEnabled() || ReplayContext.isReplay()) {
            return execution.execute(request, body);
        }

        long start = System.currentTimeMillis();
        CachedClientHttpResponse cachedResponse = null;
        Throwable failure = null;
        try {
            ClientHttpResponse response = execution.execute(request, body);
            cachedResponse = cacheResponse(response);
            return cachedResponse == null ? response : cachedResponse;
        } catch (IOException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            try {
                record(request, body, cachedResponse, failure, start, config);
            } catch (Exception e) {
                log.debug("记录出站 HTTP 调用失败，忽略", e);
            }
        }
    }

    private CachedClientHttpResponse cacheResponse(ClientHttpResponse response) {
        try {
            long contentLength = response.getHeaders().getContentLength();
            if (contentLength > MAX_CAPTURE_BYTES) {
                return null;
            }
            return new CachedClientHttpResponse(response);
        } catch (Exception e) {
            log.debug("缓存出站响应体失败，跳过响应体记录", e);
            return null;
        }
    }

    private void record(HttpRequest request, byte[] body, CachedClientHttpResponse response,
                        Throwable failure, long start,
                        AppRegistryProperties.ExternalCallRecordProperties config) {
        String method = request.getMethod() == null ? "" : request.getMethod().name();
        String url = request.getURI() == null ? "" : request.getURI().toString();
        String requestBody = MaskUtils.truncate(
                MaskUtils.maskBody(body == null ? null : new String(body, StandardCharsets.UTF_8),
                        config.getMaskFields()), config.getMaxBodyBytes());
        String requestHeaders = MaskUtils.maskHeaders(request.getHeaders().toSingleValueMap(),
                config.getMaskHeaders());
        String responseBody = response == null ? null : MaskUtils.truncate(
                MaskUtils.maskBody(new String(response.bodyBytes(), StandardCharsets.UTF_8),
                        config.getMaskFields()), config.getMaxBodyBytes());
        String responseHeaders = response == null ? null
                : MaskUtils.maskHeaders(response.getHeaders().toSingleValueMap(), config.getMaskHeaders());
        Integer responseStatus = null;
        try {
            if (response != null) {
                responseStatus = response.getStatusCode().value();
            }
        } catch (IOException ignored) {
            // 状态码读取失败时仅忽略该字段，不影响真实调用结果
        }

        ExternalCallRecordDTO record = new ExternalCallRecordDTO();
        record.setAppId(identity.appId());
        record.setTraceId(TraceContext.get());
        record.setMethod(method);
        record.setUrl(url);
        record.setRequestHeaders(requestHeaders);
        record.setRequestBody(requestBody);
        record.setResponseStatus(responseStatus);
        record.setResponseHeaders(responseHeaders);
        record.setResponseBody(responseBody);
        record.setCostMs(System.currentTimeMillis() - start);
        record.setRequestHash(MockSignature.of(method, url, requestBody));
        record.setCreatedAt(LocalDateTime.now());
        if (failure != null) {
            record.setResponseBody(MaskUtils.truncate(
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(), 512));
        }

        if (!buffer.offer(record)) {
            log.debug("出站调用队列已满，丢弃样本，累计丢弃={}", buffer.droppedCount());
        }
    }
}
