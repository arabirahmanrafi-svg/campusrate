package com.campusrate.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A "sliding window" rate limiter: allows at most {@code limit} actions per key
 * (a user id or IP address) in any {@code windowMillis} period.
 *
 * For each key we keep the times of its recent actions in a queue. To check a new action,
 * drop the times that are older than the window; if fewer than {@code limit} remain,
 * allow it and record the time.
 *
 * Kept in memory, so limits reset if the server restarts and aren't shared between
 * several servers. That's fine for one server; with more you'd store the counts in the database.
 */
public class RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> recentActions = new ConcurrentHashMap<>();

    public RateLimiter(int limit, long windowMillis, Clock clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Returns true if the action is allowed (and records it), false if the key is over its limit. */
    public boolean tryAcquire(String key) {
        long now = clock.millis();
        Deque<Long> times = recentActions.computeIfAbsent(key, k -> new ArrayDeque<>());

        // Several requests from the same user could arrive at once, so only one thread
        // may look at this user's queue at a time.
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** Removes keys with no recent actions so memory doesn't grow forever. */
    public void cleanUp() {
        long cutoff = clock.millis() - windowMillis;
        recentActions.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long newest = entry.getValue().peekLast();
                return newest == null || newest <= cutoff;
            }
        });
    }
}
