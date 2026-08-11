package notification.spi.channel;

import notification.domain.ChannelType;
import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;

/** Strategy: one implementation per ChannelType. */
public interface Channel {
    ChannelType type();

    DeliveryResult deliver(RenderedMessage msg, ProviderContext ctx);
}
