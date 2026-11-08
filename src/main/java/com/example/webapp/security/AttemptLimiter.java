package com.example.webapp.security;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.*;

/** Bounded, single-instance rate limits. Remote addresses are taken from the socket, not client headers. */
@Component
public class AttemptLimiter {
    private record Window(long endsAt, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public AttemptLimiter() { this(Clock.systemUTC()); }
    AttemptLimiter(Clock clock) { this.clock = clock; }
    public synchronized boolean allow(String key, int maximum, long durationMillis) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || window.endsAt() <= now) {
            windows.entrySet().removeIf(entry -> entry.getValue().endsAt() <= now);
            if (windows.size() >= 10_000) return false;
            window = new Window(now + durationMillis, 0);
        }
        if (window.count() >= maximum) return false;
        windows.put(key, new Window(window.endsAt(), window.count() + 1));return true;
    }
}
