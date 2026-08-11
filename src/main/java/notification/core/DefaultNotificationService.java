package notification.core;

import notification.api.DeliveryHandle;
import notification.api.NotificationRequest;
import notification.api.NotificationService;
import notification.domain.DeliveryRecord;
import notification.domain.DeliveryStatus;
import notification.domain.ProviderContext;
import notification.domain.Recipient;
import notification.domain.TenantContext;
import notification.spi.port.NotificationRepository;
import notification.spi.port.Outbox;
import notification.spi.port.ProviderConfigRegistry;
import notification.spi.port.RateLimiter;
import notification.spi.port.RecipientDirectory;
import notification.spi.port.TenantResolver;

import java.util.Optional;

/** Synchronous boundary: validates, dedupes, rate-limits, then hands off to the async worker via the outbox. */
public final class DefaultNotificationService implements NotificationService {

    private final TenantResolver tenantResolver;
    private final RecipientDirectory recipientDirectory;
    private final NotificationRepository repo;
    private final RateLimiter rateLimiter;
    private final ProviderConfigRegistry providerConfigRegistry;
    private final Outbox outbox;

    public DefaultNotificationService(TenantResolver tenantResolver,
                                       RecipientDirectory recipientDirectory,
                                       NotificationRepository repo,
                                       RateLimiter rateLimiter,
                                       ProviderConfigRegistry providerConfigRegistry,
                                       Outbox outbox) {
        this.tenantResolver = tenantResolver;
        this.recipientDirectory = recipientDirectory;
        this.repo = repo;
        this.rateLimiter = rateLimiter;
        this.providerConfigRegistry = providerConfigRegistry;
        this.outbox = outbox;
    }

    @Override
    public DeliveryHandle send(NotificationRequest req) {
        // 1) Resolve tenant ONCE at a single boundary; fail closed on unknown tenant
        //    (UnknownTenantException propagates — caller gets a hard rejection, not a silent drop).
        TenantContext ctx = tenantResolver.resolve(req.tenantId());

        // 2) Idempotency: same requestId -> return the existing handle, do nothing else.
        Optional<DeliveryRecord> existing = repo.findByKey(ctx.tenantId(), req.requestId());
        if (existing.isPresent()) {
            return toHandle(existing.get());
        }

        // 3) Recipient preferences: opted-out sends are rejected before they ever reach a provider,
        //    but still recorded (once, thanks to the idempotency key) for audit visibility.
        Recipient recipient = recipientDirectory.find(ctx.tenantId(), req.recipientId());
        if (!recipient.preferences().allows(req.channel(), req.priority())) {
            ProviderContext providerCtx = providerConfigRegistry.resolve(ctx.tenantId(), req.channel());
            DeliveryRecord rejected = toPendingRecord(ctx.tenantId(), req, providerCtx);
            rejected.markDead("recipient opted out of " + req.channel());
            repo.save(rejected);
            return toHandle(rejected);
        }

        // 4) Noisy-neighbour protection: per-tenant, per-channel rate limit.
        if (!rateLimiter.tryAcquire(ctx.tenantId(), req.channel())) {
            return DeliveryHandle.throttled(req.requestId());
        }

        // 5) Outbox: persist state + enqueue the event in ONE transaction.
        ProviderContext providerCtx = providerConfigRegistry.resolve(ctx.tenantId(), req.channel());
        DeliveryRecord record = toPendingRecord(ctx.tenantId(), req, providerCtx);
        repo.runInTransaction(() -> {
            repo.save(record);
            outbox.enqueue(record.id()); // atomic => no "saved but event lost"
        });

        return toHandle(record); // PENDING; delivery completes asynchronously
    }

    @Override
    public DeliveryStatus status(String tenantId, String requestId) {
        return repo.findByKey(tenantId, requestId)
                .map(DeliveryRecord::status)
                .orElse(DeliveryStatus.PENDING);
    }

    // domain must not depend on api, so the NotificationRequest -> DeliveryRecord and
    // DeliveryRecord -> DeliveryHandle translations live here instead of on the domain types.

    private static DeliveryRecord toPendingRecord(String tenantId, NotificationRequest req, ProviderContext providerCtx) {
        return DeliveryRecord.pending(tenantId, req.requestId(), req.recipientId(), req.channel(),
                req.templateId(), req.params(), req.priority(), providerCtx);
    }

    private static DeliveryHandle toHandle(DeliveryRecord record) {
        return DeliveryHandle.of(record.requestId(), record.status());
    }
}
