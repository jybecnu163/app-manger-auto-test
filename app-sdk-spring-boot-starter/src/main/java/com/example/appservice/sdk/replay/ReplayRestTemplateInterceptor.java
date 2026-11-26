package com.example.appservice.sdk.replay;
import com.example.appservice.sdk.config.AppRegistryProperties;
import com.example.appservice.sdk.context.ReplayContext;
import com.example.appservice.sdk.http.MockClientHttpResponse;
import com.example.appservice.sdk.registry.AppIdentityResolver;
import com.example.appservice.sdk.util.MaskUtils;
import com.example.appservice.sdk.util.MockSignature;

import com.example.appservice.common.dto.MockResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 回放态 RestTemplate 拦截器，按请求指纹返回 Mock 响应，未命中时阻止真实外部调用。
 */
public class ReplayRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ReplayRestTemplateInterceptor.class);

    private final AppRegistryProperties properties;
    private final MockResponseStore store;
    private final AppIdentityResolver identity;

    /**
     * 构造 ReplayRestTemplateInterceptor 实例。
     */
    public ReplayRestTemplateInterceptor(AppRegistryProperties properties,
                                         MockResponseStore store,
                                         AppIdentityResolver identity) {
        this.properties = properties;
        this.store = store;
        this.identity = identity;
    }

    /**
     * 命中 Mock 则返回模拟响应；未命中则抛出 ReplayMockMissingException。
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        // 非回放请求保持原始调用链，不进入 Mock 匹配逻辑。
        if (!ReplayContext.isReplay()) {
            return execution.execute(request, body);
        }

        String method = request.getMethod() == null ? "" : request.getMethod().name();
        String url = request.getURI() == null ? "" : request.getURI().toString();
        store.load(ReplayContext.replayId());
        String maskedBody = MaskUtils.truncate(
                MaskUtils.maskBody(body == null ? null : new String(body, StandardCharsets.UTF_8),
                        properties.getExternalCallRecord().getMaskFields()),
                properties.getExternalCallRecord().getMaxBodyBytes());
        String requestHash = MockSignature.of(method, url, maskedBody);
        MockResponseDTO rule = store.find(requestHash);
        if (rule == null) {
            writeStatsHeaders();
            log.warn("Dry-Run Mock 未命中，已阻止真实调用：{} {}", method, url);
            // Mock 未命中时宁可失败，也不允许回退到真实外部调用。
            throw new ReplayMockMissingException("外部调用 Mock 未命中：" + method + " " + url);
        }
        writeStatsHeaders();
        log.info("Dry-Run Mock 命中：{} {} hash={}", method, url, requestHash);
        return new MockClientHttpResponse(rule);
    }

    /**
     * 将 Mock 命中/未命中统计写入 HTTP 响应头。
     */
    private void writeStatsHeaders() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getResponse() != null) {
            attributes.getResponse().setHeader("X-App-Replay-Mock-Hits", String.valueOf(store.hits()));
            attributes.getResponse().setHeader("X-App-Replay-Mock-Misses", String.valueOf(store.misses()));
        }
    }
}
