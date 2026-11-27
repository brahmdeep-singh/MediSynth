package com.personal.medisynth.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Sliding-window limiter kept in memory. Good enough for one application instance;
 * for several instances use a shared store (e.g. Redis) or enforce limits at the gateway.
 */
@Service
public class RateLimiter {

    private static final int PURGE_THRESHOLD = 10_000;
    private static final long STALE_AFTER_MS = 3_600_000L;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** @return true if the call is allowed (and recorded), false if the limit is exceeded. */
    public boolean tryAcquire(String key, int max, long windowMs) {
        long now = System.currentTimeMillis();
        if (hits.size() > PURGE_THRESHOLD) {
            purge(now);
        }
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMs) {
                deque.pollFirst();
            }
            if (deque.size() >= max) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    private void purge(long now) {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last > STALE_AFTER_MS;
            }
        });
    }
}
