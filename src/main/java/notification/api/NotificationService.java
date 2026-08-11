package notification.api;

import notification.domain.DeliveryStatus;

public interface NotificationService {
    /** Idempotent: same (tenantId, requestId) returns the original handle without resending. */
    DeliveryHandle send(NotificationRequest req);

    DeliveryStatus status(String tenantId, String requestId);
}
