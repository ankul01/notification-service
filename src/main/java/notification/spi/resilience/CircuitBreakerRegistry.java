package notification.spi.resilience;

import notification.domain.ChannelType;

public interface CircuitBreakerRegistry {
    CircuitBreaker forProvider(ChannelType type);
}
