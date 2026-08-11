package notification.infra.memory;

import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.port.ProviderClient;

/** Always fails. Stand-in for a vendor outage; demonstrates retry exhaustion -> breaker trip -> DLQ. */
public final class AlwaysFailingProviderClient implements ProviderClient {
    @Override
    public DeliveryResult send(RenderedMessage msg, ProviderContext ctx) {
        return DeliveryResult.failure("simulated vendor outage");
    }
}
