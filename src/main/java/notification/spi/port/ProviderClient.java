package notification.spi.port;

import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;

/** Wraps a single external vendor call (Twilio/SES/FCM/etc). Called only from inside breaker+retry. */
public interface ProviderClient {
    DeliveryResult send(RenderedMessage msg, ProviderContext ctx);
}
