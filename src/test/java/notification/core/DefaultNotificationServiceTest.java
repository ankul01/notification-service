package notification.core;

import notification.api.DeliveryHandle;
import notification.api.NotificationRequest;
import notification.domain.ChannelType;
import notification.domain.DeliveryRecord;
import notification.domain.DeliveryStatus;
import notification.domain.IsolationTier;
import notification.domain.Priority;
import notification.domain.ProviderContext;
import notification.domain.Recipient;
import notification.domain.RecipientPreferences;
import notification.domain.TenantContext;
import notification.domain.UnknownTenantException;
import notification.spi.port.NotificationRepository;
import notification.spi.port.Outbox;
import notification.spi.port.ProviderConfigRegistry;
import notification.spi.port.RateLimiter;
import notification.spi.port.RecipientDirectory;
import notification.spi.port.TenantResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNotificationServiceTest {

    @Mock private TenantResolver tenantResolver;
    @Mock private RecipientDirectory recipientDirectory;
    @Mock private NotificationRepository repo;
    @Mock private RateLimiter rateLimiter;
    @Mock private ProviderConfigRegistry providerConfigRegistry;
    @Mock private Outbox outbox;

    private DefaultNotificationService service;

    private final TenantContext acmeCtx = new TenantContext("acme", IsolationTier.POOLED);
    private final ProviderContext providerCtx =
            new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");

    @BeforeEach
    void setUp() {
        service = new DefaultNotificationService(
                tenantResolver, recipientDirectory, repo, rateLimiter, providerConfigRegistry, outbox);
    }

    @Test
    void send_unknownTenant_propagatesTheResolverException() {
        when(tenantResolver.resolve("ghost")).thenThrow(new UnknownTenantException("ghost"));

        assertThatThrownBy(() -> service.send(request("ghost", "req-1")))
                .isInstanceOf(UnknownTenantException.class);

        verifyNoInteractions(repo, rateLimiter, providerConfigRegistry, outbox);
    }

    @Test
    void send_duplicateRequestId_returnsTheExistingHandleWithoutTouchingRateLimiterOrOutbox() {
        when(tenantResolver.resolve("acme")).thenReturn(acmeCtx);
        DeliveryRecord existing = DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of(), Priority.TRANSACTIONAL, providerCtx);
        when(repo.findByKey("acme", "req-1")).thenReturn(Optional.of(existing));

        DeliveryHandle handle = service.send(request("acme", "req-1"));

        assertThat(handle.requestId()).isEqualTo("req-1");
        assertThat(handle.status()).isEqualTo(DeliveryStatus.PENDING);
        verifyNoInteractions(rateLimiter, outbox);
        verify(repo, never()).save(any());
    }

    @Test
    void send_optedOutRecipient_savesADeadRecordAndNeverConsultsTheRateLimiter() {
        when(tenantResolver.resolve("acme")).thenReturn(acmeCtx);
        when(repo.findByKey("acme", "req-1")).thenReturn(Optional.empty());
        Recipient optedOut = new Recipient("alice", "acme", "alice@example.com", null, null,
                new RecipientPreferences(EnumSet.of(ChannelType.EMAIL), true));
        when(recipientDirectory.find("acme", "alice")).thenReturn(optedOut);
        when(providerConfigRegistry.resolve("acme", ChannelType.EMAIL)).thenReturn(providerCtx);

        DeliveryHandle handle = service.send(request("acme", "req-1"));

        assertThat(handle.status()).isEqualTo(DeliveryStatus.DEAD);
        verify(repo).save(argThatDeadRecordWithReasonContaining("opted out"));
        verifyNoInteractions(rateLimiter, outbox);
    }

    @Test
    void send_rateLimited_returnsThrottledHandle() {
        when(tenantResolver.resolve("acme")).thenReturn(acmeCtx);
        when(repo.findByKey("acme", "req-1")).thenReturn(Optional.empty());
        when(recipientDirectory.find("acme", "alice")).thenReturn(allowingRecipient());
        when(rateLimiter.tryAcquire("acme", ChannelType.EMAIL)).thenReturn(false);

        DeliveryHandle handle = service.send(request("acme", "req-1"));

        assertThat(handle.status()).isEqualTo(DeliveryStatus.THROTTLED);
        assertThat(handle.requestId()).isEqualTo("req-1");
        verifyNoInteractions(outbox);
    }

    @Test
    void send_happyPath_savesPendingRecordAndEnqueuesItInsideTheTransaction() {
        when(tenantResolver.resolve("acme")).thenReturn(acmeCtx);
        when(repo.findByKey("acme", "req-1")).thenReturn(Optional.empty());
        when(recipientDirectory.find("acme", "alice")).thenReturn(allowingRecipient());
        when(rateLimiter.tryAcquire("acme", ChannelType.EMAIL)).thenReturn(true);
        when(providerConfigRegistry.resolve("acme", ChannelType.EMAIL)).thenReturn(providerCtx);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(repo).runInTransaction(any());

        DeliveryHandle handle = service.send(request("acme", "req-1"));

        assertThat(handle.requestId()).isEqualTo("req-1");
        assertThat(handle.status()).isEqualTo(DeliveryStatus.PENDING);
        verify(repo).save(argThatPendingRecordFor("req-1"));
        verify(outbox).enqueue(any());
    }

    @Test
    void status_unknownRequest_reportsPending() {
        when(repo.findByKey("acme", "no-such-req")).thenReturn(Optional.empty());

        assertThat(service.status("acme", "no-such-req")).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    void status_knownRequest_reflectsTheStoredRecordStatus() {
        DeliveryRecord record = DeliveryRecord.pending("acme", "req-1", "alice", ChannelType.EMAIL,
                "welcome-email", Map.of(), Priority.TRANSACTIONAL, providerCtx);
        record.markSent();
        when(repo.findByKey("acme", "req-1")).thenReturn(Optional.of(record));

        assertThat(service.status("acme", "req-1")).isEqualTo(DeliveryStatus.SENT);
    }

    private Recipient allowingRecipient() {
        return new Recipient("alice", "acme", "alice@example.com", null, null,
                new RecipientPreferences(EnumSet.noneOf(ChannelType.class), true));
    }

    private static NotificationRequest request(String tenantId, String requestId) {
        return new NotificationRequest(requestId, tenantId, "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL);
    }

    private static DeliveryRecord argThatDeadRecordWithReasonContaining(String fragment) {
        return org.mockito.ArgumentMatchers.argThat(r ->
                r.status() == DeliveryStatus.DEAD && r.deadReason() != null && r.deadReason().contains(fragment));
    }

    private static DeliveryRecord argThatPendingRecordFor(String requestId) {
        return org.mockito.ArgumentMatchers.argThat(r ->
                r.status() == DeliveryStatus.PENDING && r.requestId().equals(requestId));
    }
}
