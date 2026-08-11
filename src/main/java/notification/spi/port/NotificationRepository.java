package notification.spi.port;

import notification.domain.DeliveryRecord;

import java.util.Optional;

public interface NotificationRepository {
    /** Idempotency lookup, keyed by (tenantId, requestId). */
    Optional<DeliveryRecord> findByKey(String tenantId, String requestId);

    void save(DeliveryRecord record);

    void update(DeliveryRecord record);

    DeliveryRecord load(String recordId);

    /** Runs save()+outbox enqueue atomically; swap for a real @Transactional boundary in prod. */
    void runInTransaction(Runnable work);
}
