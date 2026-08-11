package notification.spi.port;

import notification.domain.ChannelType;

/** Per-tenant, per-channel noisy-neighbour protection. */
public interface RateLimiter {
    boolean tryAcquire(String tenantId, ChannelType channel);
}
