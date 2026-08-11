package notification.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The aggregate that tracks a single notification request from PENDING through to a terminal
 * status. Persisted by NotificationRepository and looked up by (tenantId, requestId) for
 * idempotency.
 */
public final class DeliveryRecord {
    private final String id;
    private final String tenantId;
    private final String requestId;
    private final String recipientId;
    private final ChannelType channel;
    private final String templateId;
    private final Map<String, Object> params;
    private final Priority priority;
    private final ProviderContext providerContext;
    private final List<DeliveryAttempt> attempts = new ArrayList<>();
    private final Instant createdAt;

    private DeliveryStatus status;
    private String deadReason;
    private Instant updatedAt;

    private DeliveryRecord(String id, String tenantId, String requestId, String recipientId, ChannelType channel,
                            String templateId, Map<String, Object> params, Priority priority,
                            ProviderContext providerContext, DeliveryStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.requestId = requestId;
        this.recipientId = recipientId;
        this.channel = channel;
        this.templateId = templateId;
        this.params = params;
        this.priority = priority;
        this.providerContext = providerContext;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Built from value objects, not the inbound DTO — domain must not depend on the api package.
     * Callers (core) translate NotificationRequest into these arguments.
     */
    public static DeliveryRecord pending(String tenantId, String requestId, String recipientId, ChannelType channel,
                                          String templateId, Map<String, Object> params, Priority priority,
                                          ProviderContext providerContext) {
        return new DeliveryRecord(UUID.randomUUID().toString(), tenantId, requestId, recipientId, channel,
                templateId, params, priority, providerContext, DeliveryStatus.PENDING);
    }

    public void addAttempt(DeliveryAttempt attempt) {
        attempts.add(attempt);
        touch();
    }

    public void markSent() {
        this.status = DeliveryStatus.SENT;
        touch();
    }

    public void markDead(String reason) {
        this.status = DeliveryStatus.DEAD;
        this.deadReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String requestId() {
        return requestId;
    }

    public String recipientId() {
        return recipientId;
    }

    public ChannelType channel() {
        return channel;
    }

    public String templateId() {
        return templateId;
    }

    public Map<String, Object> params() {
        return params;
    }

    public Priority priority() {
        return priority;
    }

    public ProviderContext providerContext() {
        return providerContext;
    }

    public DeliveryStatus status() {
        return status;
    }

    public String deadReason() {
        return deadReason;
    }

    public List<DeliveryAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
