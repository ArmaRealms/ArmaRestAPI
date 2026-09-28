package me.fredthedoggy.restpapi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class RequestLimiter {
    private final int limit;
    private final long windowNanos;
    private final Map<String, Window> windows = new HashMap<>();

    RequestLimiter(int limit, int windowSeconds) {
        this.limit = limit;
        windowNanos = TimeUnit.SECONDS.toNanos(windowSeconds);
    }

    synchronized boolean allow(String ip) {
        long now = System.nanoTime();
        Window window = windows.get(ip);
        if (window == null || now - window.start >= windowNanos) {
            if (windows.size() >= 4096) {
                Iterator<Window> iterator = windows.values().iterator();
                while (iterator.hasNext()) {
                    if (now - iterator.next().start >= windowNanos) iterator.remove();
                }
                if (windows.size() >= 4096) return false;
            }
            windows.put(ip, new Window(now));
            return true;
        }
        return ++window.count <= limit;
    }

    private static final class Window {
        private final long start;
        private int count = 1;
        private Window(long start) { this.start = start; }
    }
}
