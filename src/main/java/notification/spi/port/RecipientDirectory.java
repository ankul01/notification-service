package notification.spi.port;

import notification.domain.Recipient;

/** Looks up recipient contact info + preferences for the preference check in the send path. */
public interface RecipientDirectory {
    Recipient find(String tenantId, String recipientId);
}
