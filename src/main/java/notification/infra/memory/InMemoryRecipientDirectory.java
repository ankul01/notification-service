package notification.infra.memory;

import notification.domain.Recipient;
import notification.spi.port.RecipientDirectory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRecipientDirectory implements RecipientDirectory {
    private final Map<String, Recipient> recipients = new ConcurrentHashMap<>();

    public InMemoryRecipientDirectory register(Recipient recipient) {
        recipients.put(key(recipient.tenantId(), recipient.recipientId()), recipient);
        return this;
    }

    @Override
    public Recipient find(String tenantId, String recipientId) {
        Recipient recipient = recipients.get(key(tenantId, recipientId));
        if (recipient == null) {
            throw new IllegalArgumentException("Unknown recipient: " + recipientId + " for tenant " + tenantId);
        }
        return recipient;
    }

    private static String key(String tenantId, String recipientId) {
        return tenantId + ":" + recipientId;
    }
}
