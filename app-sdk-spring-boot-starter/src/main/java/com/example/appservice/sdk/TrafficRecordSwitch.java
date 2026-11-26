package com.example.appservice.sdk;

import java.time.LocalDateTime;

/**
 * 线程安全的流量录制开关，支持按生效起始时间延迟开启。
 */
public class TrafficRecordSwitch {

    private volatile boolean enabled;
    private volatile LocalDateTime startAt;

    public TrafficRecordSwitch(boolean enabled, LocalDateTime startAt) {
        this.enabled = enabled;
        this.startAt = startAt;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    /**
     * 判断录制是否已开启且已过生效起始时间。
     */
    public boolean isActive() {
        return enabled && (startAt == null || !LocalDateTime.now().isBefore(startAt));
    }

    /**
     * 更新录制开关；关闭时清空生效时间。
     */
    public void set(boolean enabled, LocalDateTime startAt) {
        this.enabled = enabled;
        this.startAt = enabled ? startAt : null;
    }
}
