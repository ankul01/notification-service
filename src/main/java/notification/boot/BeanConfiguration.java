package notification.boot;

import notification.core.DefaultNotificationService;
import notification.core.NotificationWorker;
import notification.core.RecordHandler;
import notification.core.TenantBulkheadPool;
import notification.domain.ChannelType;
import notification.domain.IsolationTier;
import notification.domain.NotificationTemplate;
import notification.domain.ProviderContext;
import notification.domain.Recipient;
import notification.domain.RecipientPreferences;
import notification.infra.channel.DefaultChannelFactory;
import notification.infra.channel.ProviderBackedChannel;
import notification.infra.memory.InMemoryDeadLetterQueue;
import notification.infra.memory.InMemoryNotificationRepository;
import notification.infra.memory.InMemoryOutbox;
import notification.infra.memory.InMemoryProviderConfigRegistry;
import notification.infra.memory.InMemoryRateLimiter;
import notification.infra.memory.InMemoryRecipientDirectory;
import notification.infra.memory.InMemoryTenantResolver;
import notification.infra.memory.OutboxDispatcher;
import notification.infra.memory.ReliableProviderClient;
import notification.infra.memory.SimpleTemplateEngine;
import notification.infra.resilience.DefaultCircuitBreakerRegistry;
import notification.infra.resilience.ExponentialBackoffRetryPolicy;
import notification.infra.resilience.SimpleCircuitBreaker;
import notification.spi.channel.ChannelFactory;
import notification.spi.port.DeadLetterQueue;
import notification.spi.port.NotificationRepository;
import notification.spi.port.ProviderConfigRegistry;
import notification.spi.port.RateLimiter;
import notification.spi.port.RecipientDirectory;
import notification.spi.port.TemplateEngine;
import notification.spi.port.TenantResolver;
import notification.api.NotificationService;
import notification.spi.resilience.CircuitBreaker;
import notification.spi.resilience.CircuitBreakerRegistry;
import notification.spi.resilience.RetryPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumSet;
import java.util.Map;

/**
 * Composition root. This is the only class in the codebase allowed to know both "notification.*"
 * and "org.springframework.*" at once — domain/api/spi/core stay framework-agnostic on purpose
 * (see REVIEW.md S-01/ArchitectureRulesTest, which allows notification.boot to depend on
 * everything but forbids the reverse).
 *
 * <p>Wires the in-memory/demo adapters. Swapping in a real datastore, message broker, or vendor
 * SDK means writing a new adapter behind the same spi.port/spi.channel interfaces and changing
 * only the @Bean method here — nothing in core or domain moves.
 */
@Configuration
public class BeanConfiguration {

    @Bean
    TenantResolver tenantResolver() {
        return new InMemoryTenantResolver()
                .register("acme", IsolationTier.POOLED);
    }

    @Bean
    RecipientDirectory recipientDirectory() {
        RecipientPreferences defaultPreferences = new RecipientPreferences(EnumSet.noneOf(ChannelType.class), true);
        Recipient alice = new Recipient("alice", "acme", "alice@example.com", "+15550100",
                "demo-device-token", defaultPreferences);
        return new InMemoryRecipientDirectory().register(alice);
    }

    @Bean
    NotificationRepository notificationRepository() {
        return new InMemoryNotificationRepository();
    }

    @Bean
    RateLimiter rateLimiter() {
        // Generous demo defaults so the happy path isn't throttled; see REVIEW.md C-05 for the
        // per-tier bulkhead work this should eventually drive.
        return new InMemoryRateLimiter(Map.of(), 100, 1_000L);
    }

    @Bean
    ProviderConfigRegistry providerConfigRegistry() {
        ProviderContext acmeEmail = new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");
        return new InMemoryProviderConfigRegistry().register("acme", ChannelType.EMAIL, acmeEmail);
    }

    @Bean
    InMemoryOutbox outbox() {
        return new InMemoryOutbox();
    }

    @Bean
    TemplateEngine templateEngine() {
        NotificationTemplate welcomeEmail = new NotificationTemplate("welcome-email", "acme", ChannelType.EMAIL,
                "Welcome, {{name}}!", "Hi {{name}}, thanks for joining {{tenant}}.");
        return new SimpleTemplateEngine().register(welcomeEmail);
    }

    @Bean
    DeadLetterQueue deadLetterQueue() {
        return new InMemoryDeadLetterQueue();
    }

    @Bean
    ChannelFactory channelFactory() {
        ReliableProviderClient emailProvider = new ReliableProviderClient();
        return new DefaultChannelFactory()
                .register(new ProviderBackedChannel(ChannelType.EMAIL, emailProvider))
                .register(new ProviderBackedChannel(ChannelType.SMS, emailProvider))
                .register(new ProviderBackedChannel(ChannelType.PUSH, emailProvider))
                .register(new ProviderBackedChannel(ChannelType.IN_APP, emailProvider));
    }

    @Bean
    RetryPolicy retryPolicy() {
        return new ExponentialBackoffRetryPolicy(3, 10L);
    }

    @Bean
    CircuitBreakerRegistry circuitBreakerRegistry() {
        return new DefaultCircuitBreakerRegistry(() -> new SimpleCircuitBreaker(3, 5_000L));
    }

    @Bean
    TenantBulkheadPool tenantBulkheadPool() {
        return new TenantBulkheadPool(2);
    }

    @Bean
    NotificationService notificationService(TenantResolver tenantResolver,
                                             RecipientDirectory recipientDirectory,
                                             NotificationRepository notificationRepository,
                                             RateLimiter rateLimiter,
                                             ProviderConfigRegistry providerConfigRegistry,
                                             InMemoryOutbox outbox) {
        return new DefaultNotificationService(tenantResolver, recipientDirectory, notificationRepository,
                rateLimiter, providerConfigRegistry, outbox);
    }

    @Bean
    NotificationWorker notificationWorker(NotificationRepository notificationRepository,
                                           TemplateEngine templateEngine,
                                           ChannelFactory channelFactory,
                                           CircuitBreakerRegistry circuitBreakerRegistry,
                                           RetryPolicy retryPolicy,
                                           DeadLetterQueue deadLetterQueue) {
        return new NotificationWorker(notificationRepository, templateEngine, channelFactory,
                circuitBreakerRegistry, retryPolicy, deadLetterQueue);
    }

    @Bean
    OutboxDispatcher outboxDispatcher(InMemoryOutbox outbox, NotificationRepository notificationRepository,
                                       TenantBulkheadPool tenantBulkheadPool, RecordHandler recordHandler) {
        return new OutboxDispatcher(outbox, notificationRepository, tenantBulkheadPool, recordHandler);
    }

    /** Drives OutboxDispatcher on a daemon thread. Spring interrupts it on context shutdown, which
     * OutboxDispatcher's run loop already handles by exiting cleanly (see its catch InterruptedException). */
    @Bean(destroyMethod = "interrupt")
    Thread outboxDispatcherThread(OutboxDispatcher outboxDispatcher) {
        Thread thread = new Thread(outboxDispatcher, "outbox-dispatcher");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
