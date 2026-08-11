package notification.infra.memory;

import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.port.ProviderClient;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Fails the first N calls, then succeeds. Demonstrates retry recovering a transient vendor blip. */
public final class FlakyProviderClient implements ProviderClient {
    private final int failuresBeforeSuccess;
    private final AtomicInteger callCount = new AtomicInteger();

    public FlakyProviderClient(int failuresBeforeSuccess) {
        this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    @Override
    public DeliveryResult send(RenderedMessage msg, ProviderContext ctx) {
        int attempt = callCount.incrementAndGet();
        if (attempt <= failuresBeforeSuccess) {
            return DeliveryResult.failure("simulated transient vendor timeout (attempt " + attempt + ")");
        }
        return DeliveryResult.success(UUID.randomUUID().toString());
    }
}
