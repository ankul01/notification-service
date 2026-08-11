package notification.infra.memory;

import notification.domain.ChannelType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

    @Test
    void allowsUpToTheLimitWithinTheWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Map.of(), 3, 10_000L);

        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
    }

    @Test
    void deniesOnceTheLimitIsExceededWithinTheWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Map.of(), 2, 10_000L);

        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isFalse();
    }

    @Test
    void tracksEachTenantChannelPairIndependently() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Map.of(), 1, 10_000L);

        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        // Different channel, same tenant: independent bucket.
        assertThat(limiter.tryAcquire("acme", ChannelType.SMS)).isTrue();
        // Different tenant, same channel: independent bucket.
        assertThat(limiter.tryAcquire("other-tenant", ChannelType.EMAIL)).isTrue();
        // Back to the first bucket: already exhausted.
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isFalse();
    }

    @Test
    void perTenantLimitOverridesTheDefault() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Map.of("vip-tenant", 5), 1, 10_000L);

        assertThat(limiter.tryAcquire("vip-tenant", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("vip-tenant", ChannelType.EMAIL)).isTrue();
        // Still within the per-tenant override of 5, which would already be denied under the default of 1.
        assertThat(limiter.tryAcquire("vip-tenant", ChannelType.EMAIL)).isTrue();
    }

    @Test
    void resetsAfterTheWindowElapses() throws InterruptedException {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Map.of(), 1, 30L);

        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isFalse();

        Thread.sleep(60L); // generous margin over the 30ms window to avoid flakiness

        assertThat(limiter.tryAcquire("acme", ChannelType.EMAIL)).isTrue();
    }
}
