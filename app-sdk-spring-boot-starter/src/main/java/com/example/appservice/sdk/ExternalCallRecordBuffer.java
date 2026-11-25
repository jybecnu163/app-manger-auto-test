package com.example.appservice.sdk;

import com.example.appservice.common.dto.ExternalCallRecordDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class ExternalCallRecordBuffer {

    private final BlockingQueue<ExternalCallRecordDTO> queue;
    private final AtomicLong dropped = new AtomicLong();

    public ExternalCallRecordBuffer(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    }

    public boolean offer(ExternalCallRecordDTO record) {
        if (record == null) {
            return true;
        }
        if (queue.offer(record)) {
            return true;
        }
        dropped.incrementAndGet();
        return false;
    }

    public List<ExternalCallRecordDTO> drain(int limit) {
        List<ExternalCallRecordDTO> batch = new ArrayList<>(Math.max(1, limit));
        queue.drainTo(batch, Math.max(1, limit));
        return batch;
    }

    public int size() {
        return queue.size();
    }

    public long droppedCount() {
        return dropped.get();
    }
}
