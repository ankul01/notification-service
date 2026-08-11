package notification.infra.memory;

import notification.spi.port.Outbox;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Stand-in for a durable outbox table + CDC/poller; here it's just an in-process queue. take() is
 * not part of the Outbox port — a real outbox is drained by a relay or broker consumer, not
 * polled directly, so that draining shape is memory-package-specific and stays out of spi.
 * OutboxDispatcher, in this same package, is the only caller.
 */
public final class InMemoryOutbox implements Outbox {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(String recordId) {
        queue.add(recordId);
    }

    String take() throws InterruptedException {
        return queue.take();
    }
}
