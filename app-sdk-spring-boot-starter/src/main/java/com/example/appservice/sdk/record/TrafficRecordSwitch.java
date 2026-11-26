package com.example.appservice.sdk.record;

import java.time.LocalDateTime;

/**
 * 线程安全的流量录制开关，支持按生效起始时间延迟开启。
 */
public class TrafficRecordSwitch {

    private volatile boolean enabled;
    private volatile LocalDateTime startAt;

    /**
     * 构造 TrafficRecordSwitch 实例。
     */
    public TrafficRecordSwitch(boolean enabled, LocalDateTime startAt) {
        this.enabled = enabled;
        this.startAt = startAt;
    }

    /**
     * 判断 enabled。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 返回 start at。
     */
    public LocalDateTime getStartAt() {
        return startAt;
    }

    /**
     * 判断录制是否已开启且已过生效起始时间。
     */
    public boolean isActive() {
        // 起始时间为空表示立即生效；未来时间到达前保持关闭。
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
