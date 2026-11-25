package com.example.appservice.sdk;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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

    public ReplayFilter(AppRegistryProperties properties, NonceCache nonceCache,
                        ObjectProvider<PlatformTransactionManager> transactionManagers,
                        MockResponseStore mockStore) {
        this.properties = properties;
        this.nonceCache = nonceCache;
        this.transactionManagers = transactionManagers;
        this.mockStore = mockStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (!Boolean.parseBoolean(request.getHeader(H_REPLAY))) {
                filterChain.doFilter(request, response);
                return;
            }
            AppRegistryProperties.ReplayProperties config = properties.getReplay();
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
            } finally {
                response.setHeader("X-App-Replay-Mock-Hits", String.valueOf(mockStore.hits()));
                response.setHeader("X-App-Replay-Mock-Misses", String.valueOf(mockStore.misses()));
            }
        } finally {
            ReplayContext.clear();
        }
    }

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

    private void forbidden(HttpServletResponse response, String message) throws IOException {
        log.warn("拒绝回放请求：{}", message);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    private static class WrappedFilterException extends RuntimeException {
        WrappedFilterException(Throwable cause) {
            super(cause);
        }
    }
}
