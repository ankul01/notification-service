package notification.infra.memory;

import notification.domain.ChannelType;
import notification.spi.port.RateLimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counter per (tenantId, channel). Good enough for the LLD demo; a production
 * version would use a distributed token bucket (e.g. Redis) so limits hold across instances.
 */
public final class InMemoryRateLimiter implements RateLimiter {
    private final Map<String, Integer> perTenantLimit;
    private final int defaultLimit;
    private final long windowMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public InMemoryRateLimiter(Map<String, Integer> perTenantLimit, int defaultLimit, long windowMillis) {
        this.perTenantLimit = Map.copyOf(perTenantLimit);
        this.defaultLimit = defaultLimit;
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean tryAcquire(String tenantId, ChannelType channel) {
        int limit = perTenantLimit.getOrDefault(tenantId, defaultLimit);
        String key = tenantId + ":" + channel;
        Window window = windows.computeIfAbsent(key, k -> new Window());
        return window.tryConsume(limit, windowMillis);
    }

    private static final class Window {
        private long windowStart = System.currentTimeMillis();
        private int count = 0;

        synchronized boolean tryConsume(int limit, long windowMillis) {
            long now = System.currentTimeMillis();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
