package com.example.appservice.sdk.replay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 回放 nonce 去重缓存，基于 TTL 与容量上限防止重放攻击。
 */
public class NonceCache {

    private final ConcurrentHashMap<String, Long> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSize;

    /**
     * 构造 NonceCache 实例。
     */
    public NonceCache(long ttlMillis, int maxSize) {
        this.ttlMillis = Math.max(1L, ttlMillis);
        this.maxSize = Math.max(1, maxSize);
    }

    /**
     * 尝试写入 nonce，已存在时返回 false。
     */
    public synchronized boolean putIfAbsent(String nonce) {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (entries.size() >= maxSize) {
            entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .limit(entries.size() - maxSize + 1L)
                    .map(Map.Entry::getKey)
                    .forEach(entries::remove);
        }
        // nonce 去重用于防止回放请求被重放。
        return entries.putIfAbsent(nonce, now + ttlMillis) == null;
    }
}
