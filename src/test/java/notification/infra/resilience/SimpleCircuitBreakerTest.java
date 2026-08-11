package notification.infra.resilience;

import notification.spi.resilience.CircuitOpenException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleCircuitBreakerTest {

    @Test
    void closed_letsCallsThroughAndReturnsTheirResult() throws Exception {
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker(2, 10_000L);

        String result = breaker.run(() -> "ok");

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void opensAfterConsecutiveFailuresReachTheThreshold() throws Exception {
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker(2, 10_000L);
        AtomicInteger callsThatReachedTheAction = new AtomicInteger();

        failOnce(breaker, callsThatReachedTheAction);
        failOnce(breaker, callsThatReachedTheAction);
        // Threshold (2) reached: breaker is now OPEN and must reject without invoking the action.
        assertThatThrownBy(() -> breaker.run(() -> {
            callsThatReachedTheAction.incrementAndGet();
            return "should not run";
        })).isInstanceOf(CircuitOpenException.class);

        assertThat(callsThatReachedTheAction.get()).isEqualTo(2); // only the two real failures, not the rejected call
    }

    @Test
    void afterCooldown_admitsExactlyOneProbeAndClosesOnSuccess() throws Exception {
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker(1, 20L);

        failOnce(breaker, new AtomicInteger()); // trips it open (threshold 1)
        assertThatThrownBy(() -> breaker.run(() -> "rejected while open"))
                .isInstanceOf(CircuitOpenException.class);

        Thread.sleep(40L); // generous margin past the 20ms cooldown

        String probeResult = breaker.run(() -> "probe succeeded");
        assertThat(probeResult).isEqualTo("probe succeeded");

        // Closed again: a normal call goes through without needing another cooldown wait.
        String afterClose = breaker.run(() -> "closed again");
        assertThat(afterClose).isEqualTo("closed again");
    }

    @Test
    void aFailedProbeInHalfOpenReopensImmediately() throws Exception {
        SimpleCircuitBreaker breaker = new SimpleCircuitBreaker(1, 20L);

        failOnce(breaker, new AtomicInteger()); // trips it open
        Thread.sleep(40L); // now eligible to go HALF_OPEN

        assertThatThrownBy(() -> breaker.run(() -> {
            throw new RuntimeException("probe also fails");
        })).isInstanceOf(RuntimeException.class);

        // A single HALF_OPEN failure reopens immediately, without needing failureThreshold more failures.
        assertThatThrownBy(() -> breaker.run(() -> "should be rejected"))
                .isInstanceOf(CircuitOpenException.class);
    }

    private static void failOnce(SimpleCircuitBreaker breaker, AtomicInteger calls) {
        assertThatThrownBy(() -> breaker.run(() -> {
            calls.incrementAndGet();
            throw new RuntimeException("simulated failure");
        })).isInstanceOf(RuntimeException.class);
    }
}
