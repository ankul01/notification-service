package notification.domain;

/** Resolved once at the service boundary, then threaded through the rest of the flow. */
public final class TenantContext {
    private final String tenantId;
    private final IsolationTier tier;

    public TenantContext(String tenantId, IsolationTier tier) {
        this.tenantId = tenantId;
        this.tier = tier;
    }

    public String tenantId() {
        return tenantId;
    }

    public IsolationTier tier() {
        return tier;
    }
}
