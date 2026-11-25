package com.example.appservice.sdk;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NonceCache {

    private final ConcurrentHashMap<String, Long> entries = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final int maxSize;

    public NonceCache(long ttlMillis, int maxSize) {
        this.ttlMillis = Math.max(1L, ttlMillis);
        this.maxSize = Math.max(1, maxSize);
    }

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
        return entries.putIfAbsent(nonce, now + ttlMillis) == null;
    }
}
