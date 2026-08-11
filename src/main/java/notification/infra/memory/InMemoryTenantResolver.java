package notification.infra.memory;

import notification.domain.IsolationTier;
import notification.domain.TenantContext;
import notification.spi.port.TenantResolver;
import notification.domain.UnknownTenantException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantResolver implements TenantResolver {
    private final Map<String, IsolationTier> tenants = new ConcurrentHashMap<>();

    public InMemoryTenantResolver register(String tenantId, IsolationTier tier) {
        tenants.put(tenantId, tier);
        return this;
    }

    @Override
    public TenantContext resolve(String tenantId) {
        IsolationTier tier = tenants.get(tenantId);
        if (tier == null) {
            throw new UnknownTenantException(tenantId);
        }
        return new TenantContext(tenantId, tier);
    }
}
