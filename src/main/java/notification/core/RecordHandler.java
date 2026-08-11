package notification.core;

/**
 * Driving port for the async side: whatever drains the outbox (an in-memory poller, a broker
 * consumer, a CDC relay) calls this per record. Infra depends on this; core never depends on
 * whatever does the draining.
 */
public interface RecordHandler {
    void handle(String recordId);
}
