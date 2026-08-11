package notification.infra.memory;

import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.port.ProviderClient;

import java.util.UUID;

/** Always succeeds. Stand-in for a healthy vendor (e.g. SES). */
public final class ReliableProviderClient implements ProviderClient {
    @Override
    public DeliveryResult send(RenderedMessage msg, ProviderContext ctx) {
        return DeliveryResult.success(UUID.randomUUID().toString());
    }
}
