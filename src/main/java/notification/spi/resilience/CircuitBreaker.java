package notification.spi.resilience;

import java.util.concurrent.Callable;

/** One breaker per (tenant tier x provider); trips open when a provider is unhealthy so failures don't cascade. */
public interface CircuitBreaker {
    <T> T run(Callable<T> action) throws CircuitOpenException;
}
