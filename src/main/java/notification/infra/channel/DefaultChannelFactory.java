package notification.infra.channel;

import notification.domain.ChannelType;
import notification.spi.channel.Channel;
import notification.spi.channel.ChannelFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adding a new channel (e.g. WhatsApp) means one new Channel impl + one registration, no edits elsewhere. */
public final class DefaultChannelFactory implements ChannelFactory {
    private final Map<ChannelType, Channel> channels = new ConcurrentHashMap<>();

    public DefaultChannelFactory register(Channel channel) {
        channels.put(channel.type(), channel);
        return this;
    }

    @Override
    public Channel forType(ChannelType type) {
        Channel channel = channels.get(type);
        if (channel == null) {
            throw new IllegalStateException("No channel registered for type " + type);
        }
        return channel;
    }
}
