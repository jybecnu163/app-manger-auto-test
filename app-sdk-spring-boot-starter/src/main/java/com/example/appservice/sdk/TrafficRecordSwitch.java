package com.example.appservice.sdk;

import java.time.LocalDateTime;

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

    public boolean isActive() {
        return enabled && (startAt == null || !LocalDateTime.now().isBefore(startAt));
    }

    public void set(boolean enabled, LocalDateTime startAt) {
        this.enabled = enabled;
        this.startAt = enabled ? startAt : null;
    }
}
