package notification.infra.memory;

import notification.domain.ChannelType;
import notification.domain.ProviderContext;
import notification.spi.port.ProviderConfigRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProviderConfigRegistry implements ProviderConfigRegistry {
    private final Map<String, ProviderContext> configs = new ConcurrentHashMap<>();

    public InMemoryProviderConfigRegistry register(String tenantId, ChannelType channel, ProviderContext ctx) {
        configs.put(key(tenantId, channel), ctx);
        return this;
    }

    @Override
    public ProviderContext resolve(String tenantId, ChannelType channel) {
        ProviderContext ctx = configs.get(key(tenantId, channel));
        if (ctx == null) {
            throw new IllegalStateException("No provider configured for tenant=" + tenantId + " channel=" + channel);
        }
        return ctx;
    }

    private static String key(String tenantId, ChannelType channel) {
        return tenantId + ":" + channel;
    }
}
