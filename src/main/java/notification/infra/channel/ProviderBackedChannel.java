package notification.infra.channel;

import notification.domain.ChannelType;
import notification.domain.DeliveryResult;
import notification.domain.ProviderContext;
import notification.domain.RenderedMessage;
import notification.spi.channel.Channel;
import notification.spi.port.ProviderClient;

/**
 * A single Channel implementation parameterized by type, replacing what were four byte-identical
 * classes (EmailChannel/SmsChannel/PushChannel/InAppChannel). None of them had real per-channel
 * behavior — no address selection, no provider selection (see REVIEW.md C-06/C-07), no SMS
 * segmentation, no push payload shaping — so Strategy wasn't earning its place. Reintroduce
 * per-channel subclasses once that behavior exists; until then this is the honest shape.
 */
public final class ProviderBackedChannel implements Channel {
    private final ChannelType type;
    private final ProviderClient providerClient;

    public ProviderBackedChannel(ChannelType type, ProviderClient providerClient) {
        this.type = type;
        this.providerClient = providerClient;
    }

    @Override
    public ChannelType type() {
        return type;
    }

    @Override
    public DeliveryResult deliver(RenderedMessage msg, ProviderContext ctx) {
        return providerClient.send(msg, ctx);
    }
}
