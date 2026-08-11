package notification.core;

import notification.domain.DeliveryAttempt;
import notification.domain.DeliveryRecord;
import notification.domain.DeliveryResult;
import notification.domain.RenderedMessage;
import notification.spi.channel.Channel;
import notification.spi.channel.ChannelFactory;
import notification.spi.resilience.CircuitBreaker;
import notification.spi.resilience.CircuitBreakerRegistry;
import notification.spi.resilience.CircuitOpenException;
import notification.spi.port.DeadLetterQueue;
import notification.spi.DeliveryFailedException;
import notification.spi.port.NotificationRepository;
import notification.spi.resilience.RetriesExhaustedException;
import notification.spi.resilience.RetryPolicy;
import notification.spi.port.TemplateEngine;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Async side: consumed off the outbox queue, one call per record, running on a per-tenant bulkhead thread. */
public final class NotificationWorker implements RecordHandler {

    private final NotificationRepository repo;
    private final TemplateEngine templateEngine;
    private final ChannelFactory channelFactory;
    private final CircuitBreakerRegistry breakers; // one breaker per provider/channel
    private final RetryPolicy retryPolicy;
    private final DeadLetterQueue dlq;

    public NotificationWorker(NotificationRepository repo,
                               TemplateEngine templateEngine,
                               ChannelFactory channelFactory,
                               CircuitBreakerRegistry breakers,
                               RetryPolicy retryPolicy,
                               DeadLetterQueue dlq) {
        this.repo = repo;
        this.templateEngine = templateEngine;
        this.channelFactory = channelFactory;
        this.breakers = breakers;
        this.retryPolicy = retryPolicy;
        this.dlq = dlq;
    }

    @Override
    public void handle(String recordId) {
        DeliveryRecord record = repo.load(recordId);

        RenderedMessage msg = templateEngine.render(
                record.tenantId(), record.templateId(), record.recipientId(), record.params());

        Channel channel = channelFactory.forType(record.channel());          // Strategy + Factory
        CircuitBreaker breaker = breakers.forProvider(record.channel());
        AtomicInteger attemptCounter = new AtomicInteger();

        try {
            breaker.run(() ->                                                // Circuit breaker
                    retryPolicy.execute(() -> {                              // Retry (safe: idempotent)
                        int attemptNumber = attemptCounter.incrementAndGet();
                        DeliveryResult result = channel.deliver(msg, record.providerContext());
                        record.addAttempt(new DeliveryAttempt(
                                attemptNumber, Instant.now(), result.ok(), result.errorMessage()));
                        if (!result.ok()) {
                            throw new DeliveryFailedException(result.errorMessage());
                        }
                        return result;
                    }));

            record.markSent();
            repo.update(record);
        } catch (RetriesExhaustedException | CircuitOpenException e) {
            record.markDead(e.getMessage());
            repo.update(record);
            dlq.push(record);          // drained / replayed after the provider recovers
        }
    }
}
