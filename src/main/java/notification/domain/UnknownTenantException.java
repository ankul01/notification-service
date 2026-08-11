package notification.domain;

/** Thrown by TenantResolver when a tenantId doesn't resolve; the send path fails closed on this. */
public class UnknownTenantException extends RuntimeException {
    public UnknownTenantException(String tenantId) {
        super("Unknown tenant: " + tenantId);
    }
}
