package com.resumeai.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tiny in-memory sliding-window limiter (resets when the server restarts). */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Returns false when the key already used up its quota inside the window. */
    public boolean tryAcquire(String key, int max, Duration window) {
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
            return true;
        }
    }
}
