package com.example.appservice.sdk.record;

import com.example.appservice.common.dto.TrafficRecordDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 入站流量有界阻塞队列，队列满时计数丢弃。
 */
public class TrafficRecordBuffer {

    private final BlockingQueue<TrafficRecordDTO> queue;
    private final AtomicLong dropped = new AtomicLong();

    /**
     * 构造 TrafficRecordBuffer 实例。
     */
    public TrafficRecordBuffer(int queueSize) {
        this.queue = new ArrayBlockingQueue<>(Math.max(1, queueSize));
    }

    /**
     * 将记录放入有界队列，队列满时丢弃并计数。
     */
    public boolean offer(TrafficRecordDTO record) {
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
    public List<TrafficRecordDTO> drain(int limit) {
        List<TrafficRecordDTO> batch = new ArrayList<>(Math.max(1, limit));
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
