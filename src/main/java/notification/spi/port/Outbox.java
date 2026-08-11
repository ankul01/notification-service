package notification.spi.port;

/** Transactional outbox: enqueue() is called in the same transaction as repo.save(). */
public interface Outbox {
    void enqueue(String recordId);
}
