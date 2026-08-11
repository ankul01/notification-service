package notification.api;

import notification.domain.ChannelType;
import notification.domain.Priority;

import java.util.Collections;
import java.util.Map;

/** Inbound request. requestId is a client-supplied idempotency key, unique per tenant. */
public final class NotificationRequest {
    private final String requestId;
    private final String tenantId;
    private final String recipientId;
    private final ChannelType channel;
    private final String templateId;
    private final Map<String, Object> params;
    private final Priority priority;

    public NotificationRequest(String requestId, String tenantId, String recipientId,
                                ChannelType channel, String templateId,
                                Map<String, Object> params, Priority priority) {
        this.requestId = requestId;
        this.tenantId = tenantId;
        this.recipientId = recipientId;
        this.channel = channel;
        this.templateId = templateId;
        this.params = params == null ? Collections.emptyMap() : Map.copyOf(params);
        this.priority = priority;
    }

    public String requestId() {
        return requestId;
    }

    public String tenantId() {
        return tenantId;
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
}
