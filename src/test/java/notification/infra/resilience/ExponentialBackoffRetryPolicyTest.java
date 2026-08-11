package notification.infra.resilience;

import notification.spi.resilience.RetriesExhaustedException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExponentialBackoffRetryPolicyTest {

    // baseDelayMillis=1 keeps backoff sleeps negligible so the test suite stays fast.
    private final ExponentialBackoffRetryPolicy policy = new ExponentialBackoffRetryPolicy(3, 1L);

    @Test
    void succeedsImmediately_callsActionExactlyOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void succeedsOnALaterAttempt_withinMaxAttempts() throws Exception {
        AtomicInteger calls = new AtomicInteger();

        String result = policy.execute(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void exhaustsAllAttempts_throwsRetriesExhaustedWithCause() {
        AtomicInteger calls = new AtomicInteger();
        RuntimeException permanentFailure = new RuntimeException("permanently broken");

        assertThatThrownBy(() -> policy.execute(() -> {
            calls.incrementAndGet();
            throw permanentFailure;
        }))
                .isInstanceOf(RetriesExhaustedException.class)
                .hasCause(permanentFailure);

        assertThat(calls.get()).isEqualTo(3); // exactly maxAttempts, no more
    }
}
