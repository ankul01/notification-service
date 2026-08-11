package notification.spi.port;

import notification.domain.TenantContext;

/** The single boundary where a tenantId becomes a validated TenantContext. Fails closed. */
public interface TenantResolver {
    TenantContext resolve(String tenantId);
}
