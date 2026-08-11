package notification.infra.memory;

import notification.domain.DeliveryRecord;
import notification.spi.port.NotificationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in for a real datastore. runInTransaction() just runs the work inline; in production this
 * is the boundary that makes repo.save() + outbox.enqueue() atomic (e.g. one JDBC transaction, or
 * an outbox row written in the same DB write as the aggregate).
 */
public final class InMemoryNotificationRepository implements NotificationRepository {
    private final Map<String, DeliveryRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, DeliveryRecord> byIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Optional<DeliveryRecord> findByKey(String tenantId, String requestId) {
        return Optional.ofNullable(byIdempotencyKey.get(key(tenantId, requestId)));
    }

    @Override
    public void save(DeliveryRecord record) {
        byId.put(record.id(), record);
        byIdempotencyKey.put(key(record.tenantId(), record.requestId()), record);
    }

    @Override
    public void update(DeliveryRecord record) {
        byId.put(record.id(), record);
        byIdempotencyKey.put(key(record.tenantId(), record.requestId()), record);
    }

    @Override
    public DeliveryRecord load(String recordId) {
        DeliveryRecord record = byId.get(recordId);
        if (record == null) {
            throw new IllegalArgumentException("No such delivery record: " + recordId);
        }
        return record;
    }

    @Override
    public void runInTransaction(Runnable work) {
        work.run();
    }

    private static String key(String tenantId, String requestId) {
        return tenantId + "::" + requestId;
    }
}
