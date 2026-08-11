package notification.infra.resilience;

import notification.domain.ChannelType;
import notification.spi.resilience.CircuitBreaker;
import notification.spi.resilience.CircuitBreakerRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class DefaultCircuitBreakerRegistry implements CircuitBreakerRegistry {
    private final Map<ChannelType, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final Supplier<CircuitBreaker> breakerFactory;

    public DefaultCircuitBreakerRegistry(Supplier<CircuitBreaker> breakerFactory) {
        this.breakerFactory = breakerFactory;
    }

    @Override
    public CircuitBreaker forProvider(ChannelType type) {
        return breakers.computeIfAbsent(type, t -> breakerFactory.get());
    }
}
