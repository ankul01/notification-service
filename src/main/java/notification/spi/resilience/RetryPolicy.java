package notification.spi.resilience;

import java.util.concurrent.Callable;

/** Exponential backoff + jitter retry, only safe because Channel.deliver() is idempotent per attempt. */
public interface RetryPolicy {
    <T> T execute(Callable<T> action) throws RetriesExhaustedException;
}
