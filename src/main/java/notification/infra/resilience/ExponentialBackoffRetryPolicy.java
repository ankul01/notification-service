package notification.infra.resilience;

import notification.spi.resilience.RetriesExhaustedException;
import notification.spi.resilience.RetryPolicy;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/** Retries with exponential backoff + jitter. Only safe to use around idempotent actions. */
public final class ExponentialBackoffRetryPolicy implements RetryPolicy {
    private final int maxAttempts;
    private final long baseDelayMillis;

    public ExponentialBackoffRetryPolicy(int maxAttempts, long baseDelayMillis) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMillis = baseDelayMillis;
    }

    @Override
    public <T> T execute(Callable<T> action) throws RetriesExhaustedException {
        Exception lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastError = e;
                if (attempt < maxAttempts) {
                    sleep(backoffWithJitter(attempt));
                }
            }
        }
        throw new RetriesExhaustedException(
                "Exhausted " + maxAttempts + " attempts: " + lastError.getMessage(), lastError);
    }

    private long backoffWithJitter(int attempt) {
        long exp = baseDelayMillis * (1L << (attempt - 1));
        return exp / 2 + ThreadLocalRandom.current().nextLong(exp / 2 + 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
