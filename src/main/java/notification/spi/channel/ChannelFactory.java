package notification.spi.channel;

import notification.domain.ChannelType;

/** Factory: picks the Channel strategy implementation at runtime. */
public interface ChannelFactory {
    Channel forType(ChannelType type);
}
