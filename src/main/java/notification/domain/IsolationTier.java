package notification.domain;

/**
 * How a tenant's workload is isolated from others.
 * POOLED - shares queues/workers with other tenants (subject to fair-share limits).
 * BRIDGE - shares infra but gets dedicated rate-limit buckets and priority lanes.
 * SILO   - dedicated worker pool / queue, fully isolated from noisy neighbours.
 */
public enum IsolationTier {
    POOLED, BRIDGE, SILO
}
