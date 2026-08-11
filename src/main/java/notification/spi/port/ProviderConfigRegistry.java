package notification.spi.port;

import notification.domain.ChannelType;
import notification.domain.ProviderContext;

/** Per-tenant provider configuration (which vendor + credentials to use per channel). */
public interface ProviderConfigRegistry {
    ProviderContext resolve(String tenantId, ChannelType channel);
}
