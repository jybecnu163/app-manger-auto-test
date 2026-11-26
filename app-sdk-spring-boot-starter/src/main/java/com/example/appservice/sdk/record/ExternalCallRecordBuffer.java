package com.example.appservice.sdk.record;

import com.example.appservice.common.dto.ExternalCallRecordDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 出站 HTTP 调用记录有界队列，与入站流量缓冲对称。
 */
public class ExternalCallRecordBuffer {

    private final BlockingQueue<ExternalCallRecordDTO> queue;
    private final AtomicLong dropped = new AtomicLong();

    /**
     * 构造 ExternalCallRecordBuffer 实例。
     */
    public ExternalCallRecordBuffer(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    }

    /**
     * 将记录放入有界队列，队列满时丢弃并计数。
     */
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

    /**
     * 按上限取出待上报记录。
     */
    public List<ExternalCallRecordDTO> drain(int limit) {
        List<ExternalCallRecordDTO> batch = new ArrayList<>(Math.max(1, limit));
        queue.drainTo(batch, Math.max(1, limit));
        return batch;
    }

    /**
     * 返回当前队列中的记录数。
     */
    public int size() {
        return queue.size();
    }

    /**
     * 返回因队列满而丢弃的记录数。
     */
    public long droppedCount() {
        return dropped.get();
    }
}
