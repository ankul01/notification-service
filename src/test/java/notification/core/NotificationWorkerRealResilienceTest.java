package notification.core;

import notification.domain.ChannelType;
import notification.domain.DeliveryRecord;
import notification.domain.DeliveryStatus;
import notification.domain.NotificationTemplate;
import notification.domain.Priority;
import notification.domain.ProviderContext;
import notification.infra.channel.DefaultChannelFactory;
import notification.infra.channel.ProviderBackedChannel;
import notification.infra.memory.AlwaysFailingProviderClient;
import notification.infra.memory.FlakyProviderClient;
import notification.infra.memory.InMemoryDeadLetterQueue;
import notification.infra.memory.InMemoryNotificationRepository;
import notification.infra.memory.SimpleTemplateEngine;
import notification.infra.resilience.DefaultCircuitBreakerRegistry;
import notification.infra.resilience.ExponentialBackoffRetryPolicy;
import notification.infra.resilience.SimpleCircuitBreaker;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NotificationWorkerTest isolates the worker from its collaborators with mocks. This test does
 * the opposite on purpose: it wires the real ExponentialBackoffRetryPolicy, SimpleCircuitBreaker
 * and DeadLetterQueue together with the FlakyProviderClient/AlwaysFailingProviderClient test
 * fixtures, to prove the actual retry-then-recover and retry-then-DLQ paths work end to end, not
 * just that NotificationWorker calls its collaborators in the right order.
 */
class NotificationWorkerRealResilienceTest {

    private final ProviderContext providerCtx =
            new ProviderContext("acme", ChannelType.EMAIL, "flaky-vendor", "no-reply@acme.example");

    @Test
    void aVendorThatRecoversWithinMaxAttempts_eventuallyMarksSent() {
        InMemoryNotificationRepository repo = new InMemoryNotificationRepository();
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine().register(
                new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL, "Hi {{name}}", "Hi {{name}}"));
        // Fails twice, succeeds on the 3rd call — recoverable within maxAttempts=3.
        DefaultChannelFactory channelFactory = new DefaultChannelFactory()
                .register(new ProviderBackedChannel(ChannelType.EMAIL, new FlakyProviderClient(2)));
        DefaultCircuitBreakerRegistry breakers =
                new DefaultCircuitBreakerRegistry(() -> new SimpleCircuitBreaker(5, 10_000L));
        InMemoryDeadLetterQueue dlq = new InMemoryDeadLetterQueue();
        NotificationWorker worker = new NotificationWorker(repo, templateEngine, channelFactory, breakers,
                new ExponentialBackoffRetryPolicy(3, 1L), dlq);

        DeliveryRecord record = pendingRecord(providerCtx);
        repo.save(record);

        worker.handle(record.id());

        assertThat(record.status()).isEqualTo(DeliveryStatus.SENT);
        assertThat(record.attempts()).hasSize(3); // 2 failures + 1 success, all recorded
        assertThat(dlq.snapshot()).isEmpty();
    }

    @Test
    void aPermanentlyDownVendor_exhaustsRetriesAndLandsInTheDlq() {
        InMemoryNotificationRepository repo = new InMemoryNotificationRepository();
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine().register(
                new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL, "Hi {{name}}", "Hi {{name}}"));
        DefaultChannelFactory channelFactory = new DefaultChannelFactory()
                .register(new ProviderBackedChannel(ChannelType.EMAIL, new AlwaysFailingProviderClient()));
        // High failure threshold so the breaker itself never trips — isolates "retries exhausted"
        // from "breaker rejected", which NotificationWorkerTest already covers separately via mocks.
        DefaultCircuitBreakerRegistry breakers =
                new DefaultCircuitBreakerRegistry(() -> new SimpleCircuitBreaker(100, 10_000L));
        InMemoryDeadLetterQueue dlq = new InMemoryDeadLetterQueue();
        NotificationWorker worker = new NotificationWorker(repo, templateEngine, channelFactory, breakers,
                new ExponentialBackoffRetryPolicy(3, 1L), dlq);

        DeliveryRecord record = pendingRecord(providerCtx);
        repo.save(record);

        worker.handle(record.id());

        assertThat(record.status()).isEqualTo(DeliveryStatus.DEAD);
        assertThat(record.attempts()).hasSize(3); // all 3 attempts made, all failed
        assertThat(record.attempts()).allSatisfy(attempt -> assertThat(attempt.success()).isFalse());
        assertThat(dlq.snapshot()).containsExactly(record);
    }

    private DeliveryRecord pendingRecord(ProviderContext ctx) {
        return DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL, ctx);
    }
}
