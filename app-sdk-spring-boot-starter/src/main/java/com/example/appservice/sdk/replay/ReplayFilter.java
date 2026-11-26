package com.example.appservice.sdk.replay;
import com.example.appservice.sdk.config.AppRegistryProperties;
import com.example.appservice.sdk.context.ReplayContext;
import com.example.appservice.sdk.context.ReplayInfo;
import com.example.appservice.sdk.util.ReplaySignatureUtil;

import com.example.appservice.common.dto.ReplayMode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Dry-Run 回放入口过滤器，校验回放头、时间窗、签名和 nonce，并在强制回滚事务中执行请求。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ReplayFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ReplayFilter.class);

    private static final String H_REPLAY = "X-App-Replay";
    private static final String H_REPLAY_ID = "X-App-Replay-Id";
    private static final String H_REPLAY_MODE = "X-App-Replay-Mode";
    private static final String H_TIMESTAMP = "X-App-Replay-Timestamp";
    private static final String H_NONCE = "X-App-Replay-Nonce";
    private static final String H_SIGNATURE = "X-App-Replay-Signature";

    private final AppRegistryProperties properties;
    private final NonceCache nonceCache;
    private final ObjectProvider<PlatformTransactionManager> transactionManagers;
    private final MockResponseStore mockStore;

    /**
     * 构造 ReplayFilter 实例。
     */
    public ReplayFilter(AppRegistryProperties properties, NonceCache nonceCache,
                        ObjectProvider<PlatformTransactionManager> transactionManagers,
                        MockResponseStore mockStore) {
        this.properties = properties;
        this.nonceCache = nonceCache;
        this.transactionManagers = transactionManagers;
        this.mockStore = mockStore;
    }

    /**
     * 校验回放请求、设置回放上下文、执行并回滚事务、输出 Mock 统计头。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (!Boolean.parseBoolean(request.getHeader(H_REPLAY))) {
                filterChain.doFilter(request, response);
                return;
            }
            AppRegistryProperties.ReplayProperties config = properties.getReplay();
            // 回放开关关闭时直接拒绝，禁止执行业务逻辑。
            if (!config.isEnabled()) {
                forbidden(response, "replay mode is disabled");
                return;
            }

            ReplayInfo info = parseInfo(request);
            if (info == null) {
                forbidden(response, "invalid replay headers");
                return;
            }
            long skew = Math.abs(System.currentTimeMillis() - info.getTimestamp());
            if (skew > config.getMaxTimestampSkew().toMillis()) {
                forbidden(response, "replay timestamp expired");
                return;
            }
            if (config.isSignatureEnabled() && !ReplaySignatureUtil.verify(config.getSignatureSecret(), info)) {
                forbidden(response, "invalid replay signature");
                return;
            }
            if (!nonceCache.putIfAbsent(info.getNonce())) {
                forbidden(response, "replay nonce already used");
                return;
            }

            ReplayContext.set(info);
            log.info("进入 Dry-Run 回放，replayId={}, mode={}", info.getReplayId(), info.getReplayMode());
            mockStore.reset();
            try {
                executeWithRollback(request, response, filterChain, config);
            } catch (ReplayMockMissingException e) {
                writeMockMissingResponse(response);
            } finally {
                // 统计头写回响应，供管理端展示 Mock 命中情况。
                response.setHeader("X-App-Replay-Mock-Hits", String.valueOf(mockStore.hits()));
                response.setHeader("X-App-Replay-Mock-Misses", String.valueOf(mockStore.misses()));
            }
        } finally {
            ReplayContext.clear();
        }
    }

    /**
     * 执行 execute with rollback 相关逻辑。
     */
    private void executeWithRollback(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain,
                                     AppRegistryProperties.ReplayProperties config)
            throws ServletException, IOException {
        PlatformTransactionManager manager = transactionManagers.getIfAvailable();
        if (manager == null) {
            log.warn("未找到 PlatformTransactionManager，本次 Dry-Run 不保证数据库回滚");
            filterChain.doFilter(request, response);
            return;
        }
        if (!"rollback".equalsIgnoreCase(config.getDbStrategy())) {
            log.warn("dbStrategy={}，本次 Dry-Run 不执行事务回滚", config.getDbStrategy());
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-App-Replay-Rolled-Back", "true");
        // 外层事务包裹业务链，回放结束后强制回滚，避免 Dry-Run 产生脏数据。
        TransactionTemplate template = new TransactionTemplate(manager);
        Throwable[] failure = new Throwable[1];
        try {
            template.execute(status -> {
                try {
                    filterChain.doFilter(request, response);
                } catch (ServletException | IOException e) {
                    failure[0] = e;
                    throw new WrappedFilterException(e);
                } catch (RuntimeException | Error e) {
                    failure[0] = e;
                    throw e;
                } finally {
                    status.setRollbackOnly();
                }
                return null;
            });
        } catch (WrappedFilterException e) {
            failure[0] = e.getCause();
        } catch (RuntimeException | Error e) {
            if (failure[0] == null) {
                failure[0] = e;
            }
        }
        rethrow(failure[0]);
    }

    /**
     * 执行 rethrow 相关逻辑。
     */
    private void rethrow(Throwable failure) throws ServletException, IOException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof ServletException) {
            throw (ServletException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new ServletException(failure);
    }

    /**
     * 从回放请求 Header 解析回放身份信息。
     */
    private ReplayInfo parseInfo(HttpServletRequest request) {
        String replayId = request.getHeader(H_REPLAY_ID);
        String mode = request.getHeader(H_REPLAY_MODE);
        String timestamp = request.getHeader(H_TIMESTAMP);
        String nonce = request.getHeader(H_NONCE);
        if (!StringUtils.hasText(replayId) || !StringUtils.hasText(mode)
                || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            return null;
        }
        ReplayInfo info = new ReplayInfo();
        info.setReplayId(replayId);
        info.setNonce(nonce);
        info.setSignature(request.getHeader(H_SIGNATURE));
        try {
            info.setReplayMode(ReplayMode.valueOf(mode));
            info.setTimestamp(Long.parseLong(timestamp));
        } catch (Exception e) {
            return null;
        }
        return info;
    }

    /**
     * 执行 write mock missing response 相关逻辑。
     */
    private void writeMockMissingResponse(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                "{\"code\":1,\"message\":\"回放失败：外部调用 Mock 未命中，已阻止真实调用\",\"data\":null}");
    }

    /**
     * 返回 403 响应，拒绝非法回放请求。
     */
    private void forbidden(HttpServletResponse response, String message) throws IOException {
        log.warn("拒绝回放请求：{}", message);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    /**
     * 携带指定 HTTP 状态码的过滤器内部异常。
     */
    private static class WrappedFilterException extends RuntimeException {
        WrappedFilterException(Throwable cause) {
            super(cause);
        }
    }
}
