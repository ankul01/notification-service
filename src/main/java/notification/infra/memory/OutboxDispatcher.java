package notification.infra.memory;

import notification.core.RecordHandler;
import notification.core.TenantBulkheadPool;
import notification.domain.DeliveryRecord;
import notification.spi.port.NotificationRepository;

/**
 * Drains the in-memory outbox and hands each record to its tenant's dedicated bulkhead pool. This
 * is an infra adapter, not core: the blocking take()-and-loop shape is specific to an in-process
 * queue (a Kafka-backed version would be a listener callback, not a loop), so it lives next to
 * InMemoryOutbox rather than in core. It depends on core only through the RecordHandler port —
 * it has no reference to NotificationWorker, the concrete implementation.
 */
public final class OutboxDispatcher implements Runnable {
    private final InMemoryOutbox outbox;
    private final NotificationRepository repo;
    private final TenantBulkheadPool bulkheadPool;
    private final RecordHandler handler;

    private volatile boolean running = true;

    public OutboxDispatcher(InMemoryOutbox outbox, NotificationRepository repo,
                             TenantBulkheadPool bulkheadPool, RecordHandler handler) {
        this.outbox = outbox;
        this.repo = repo;
        this.bulkheadPool = bulkheadPool;
        this.handler = handler;
    }

    @Override
    public void run() {
        while (running) {
            try {
                String recordId = outbox.take();
                DeliveryRecord record = repo.load(recordId);
                bulkheadPool.forTenant(record.tenantId()).submit(() -> handler.handle(recordId));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    public void stop() {
        running = false;
    }
}
