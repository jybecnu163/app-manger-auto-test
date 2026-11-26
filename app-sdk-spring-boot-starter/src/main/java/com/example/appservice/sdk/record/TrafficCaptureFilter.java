package com.example.appservice.sdk.record;
import com.example.appservice.sdk.config.AppRegistryProperties;
import com.example.appservice.sdk.context.ReplayContext;
import com.example.appservice.sdk.context.TraceContext;
import com.example.appservice.sdk.http.CachedBodyHttpServletRequest;
import com.example.appservice.sdk.http.CachedBodyHttpServletResponse;
import com.example.appservice.sdk.registry.AppIdentityResolver;
import com.example.appservice.sdk.util.MaskUtils;

import com.example.appservice.common.dto.TrafficRecordDTO;
import com.example.appservice.common.util.RequestHashUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 入站流量采集过滤器，按开关、采样、路径规则和回放态决定是否录制请求与响应。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class TrafficCaptureFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrafficCaptureFilter.class);
    private static final int MAX_CAPTURE_BYTES = 2 * 1024 * 1024;

    private final AppRegistryProperties properties;
    private final TrafficRecordBuffer buffer;
    private final AppIdentityResolver identity;
    private final TrafficRecordSwitch recorderSwitch;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造 TrafficCaptureFilter 实例。
     */
    public TrafficCaptureFilter(AppRegistryProperties properties, TrafficRecordBuffer buffer,
                                AppIdentityResolver identity, TrafficRecordSwitch recorderSwitch) {
        this.properties = properties;
        this.buffer = buffer;
        this.identity = identity;
        this.recorderSwitch = recorderSwitch;
    }

    /**
     * 缓存请求/响应体、透传链路并上报一条流量记录。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (StringUtils.hasText(request.getContextPath()) && path.startsWith(request.getContextPath())) {
            path = path.substring(request.getContextPath().length());
        }
        if (!shouldCapture(path, request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = resolveTraceId(request);
        TraceContext.set(traceId);
        long start = System.currentTimeMillis();
        CachedBodyHttpServletRequest cachedRequest = null;
        CachedBodyHttpServletResponse cachedResponse = null;
        Throwable failure = null;
        try {
            try {
                if (bodyCaptureAllowed(request)) {
                    cachedRequest = new CachedBodyHttpServletRequest(request);
                }
                cachedResponse = new CachedBodyHttpServletResponse(response);
            } catch (Exception e) {
                log.debug("流量采集包装请求/响应失败，按原对象继续处理", e);
            }
            HttpServletRequest downstreamRequest = cachedRequest == null ? request : cachedRequest;
            HttpServletResponse downstreamResponse = cachedResponse == null ? response : cachedResponse;
            try {
                filterChain.doFilter(downstreamRequest, downstreamResponse);
            } catch (ServletException | IOException e) {
                failure = e;
                throw e;
            } catch (RuntimeException | Error e) {
                failure = e;
                throw e;
            }
        } finally {
            try {
                if (cachedResponse != null) {
                    cachedResponse.copyBodyTo(response);
                }
            } catch (Exception e) {
                log.warn("回写响应体失败", e);
            }
            try {
                record(request, cachedRequest, cachedResponse, response, path, traceId, start, failure);
            } catch (Exception e) {
                log.debug("记录入站流量失败，忽略", e);
            }
            TraceContext.clear();
        }
    }

    /**
     * 判断当前请求是否应被入站流量采集。
     */
    private boolean shouldCapture(String path, HttpServletRequest request) {
        AppRegistryProperties.TrafficRecordProperties config = properties.getTrafficRecord();
        // 回放请求不再录制，避免产生回放自身的重复流量。
        if (!recorderSwitch.isActive() || ReplayContext.isReplay()) {
            return false;
        }
        for (String exclude : config.getExcludePaths()) {
            if (pathMatcher.match(exclude, path)) {
                return false;
            }
        }
        if (!config.getIncludePaths().isEmpty()) {
            boolean included = false;
            for (String include : config.getIncludePaths()) {
                if (pathMatcher.match(include, path)) {
                    included = true;
                    break;
                }
            }
            if (!included) {
                return false;
            }
        }
        double sampleRate = config.getSampleRate();
        if (sampleRate <= 0) {
            return false;
        }
        if (sampleRate < 1 && ThreadLocalRandom.current().nextDouble() >= sampleRate) {
            return false;
        }
        return bodyCaptureAllowed(request);
    }

    /**
     * 执行 body capture allowed 相关逻辑。
     */
    private boolean bodyCaptureAllowed(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return true;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("multipart/") || lower.contains("octet-stream")) {
            return false;
        }
        return request.getContentLengthLong() <= MAX_CAPTURE_BYTES;
    }

    /**
     * 执行 resolve trace id 相关逻辑。
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (!StringUtils.hasText(traceId)) {
            traceId = request.getHeader("X-Request-Id");
        }
        return StringUtils.hasText(traceId) ? traceId : UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 执行 record 相关逻辑。
     */
    private void record(HttpServletRequest request, CachedBodyHttpServletRequest cachedRequest,
                        CachedBodyHttpServletResponse cachedResponse, HttpServletResponse response,
                        String path, String traceId, long start, Throwable failure) {
        AppRegistryProperties.TrafficRecordProperties config = properties.getTrafficRecord();
        int status = cachedResponse == null ? response.getStatus() : cachedResponse.getStatus();
        boolean success = failure == null && status < 400;
        if (config.isOnlyErrors() && success) {
            return;
        }

        String rawRequestBody = cachedRequest == null ? null
                : new String(cachedRequest.bodyBytes(), StandardCharsets.UTF_8);
        String requestBody = MaskUtils.truncate(
                MaskUtils.maskBody(rawRequestBody, config.getMaskFields()), config.getMaxBodyBytes());
        byte[] responseBytes = cachedResponse == null ? new byte[0] : cachedResponse.bodyBytes();
        String responseBody = MaskUtils.truncate(
                MaskUtils.maskBody(new String(responseBytes, StandardCharsets.UTF_8), config.getMaskFields()),
                config.getMaxBodyBytes());

        TrafficRecordDTO record = new TrafficRecordDTO();
        record.setAppId(identity.appId());
        record.setInstanceKey(instanceKey(request));
        record.setTraceId(traceId);
        record.setMethod(request.getMethod());
        record.setPath(path);
        record.setQueryParams(MaskUtils.queryParamsJson(request.getParameterMap()));
        record.setHeaders(MaskUtils.maskHeaders(headerMap(request), config.getMaskHeaders()));
        record.setBody(requestBody);
        record.setResponseStatus(status);
        record.setResponseBody(responseBody);
        record.setCostMs(System.currentTimeMillis() - start);
        record.setSuccess(success);
        record.setError(failure == null ? null
                : MaskUtils.truncate(failure.getClass().getSimpleName() + ": " + failure.getMessage(), 512));
        record.setRequestHash(RequestHashUtil.of(request.getMethod(), path,
                record.getQueryParams(), requestBody));
        record.setCreatedAt(LocalDateTime.now());

        if (!buffer.offer(record)) {
            log.debug("入站流量队列已满，丢弃样本，累计丢弃={}", buffer.droppedCount());
        }
    }

    /**
     * 执行 instance key 相关逻辑。
     */
    private String instanceKey(HttpServletRequest request) {
        String host = StringUtils.hasText(properties.getAdvertiseIp())
                ? properties.getAdvertiseIp() : request.getServerName();
        return host + ":" + request.getServerPort();
    }

    /**
     * 执行 header map 相关逻辑。
     */
    private Map<String, String> headerMap(HttpServletRequest request) {
        Map<String, String> headers = new TreeMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
