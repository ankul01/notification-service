package notification.infra.memory;

import notification.domain.ChannelType;
import notification.domain.DeliveryRecord;
import notification.domain.Priority;
import notification.domain.ProviderContext;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryNotificationRepositoryTest {

    private final InMemoryNotificationRepository repo = new InMemoryNotificationRepository();
    private final ProviderContext providerContext =
            new ProviderContext("acme", ChannelType.EMAIL, "reliable-email-provider", "no-reply@acme.example");

    @Test
    void findByKey_returnsEmptyForUnknownRequest() {
        assertThat(repo.findByKey("acme", "unknown-req")).isEmpty();
    }

    @Test
    void save_thenFindByKey_roundTrips() {
        DeliveryRecord record = pendingRecord("req-1");

        repo.save(record);

        assertThat(repo.findByKey("acme", "req-1")).contains(record);
    }

    @Test
    void findByKey_isScopedPerTenant() {
        DeliveryRecord record = pendingRecord("req-1");
        repo.save(record);

        // Same requestId, different tenant: must not collide.
        assertThat(repo.findByKey("other-tenant", "req-1")).isEmpty();
    }

    @Test
    void load_returnsTheSavedRecordById() {
        DeliveryRecord record = pendingRecord("req-1");
        repo.save(record);

        assertThat(repo.load(record.id())).isSameAs(record);
    }

    @Test
    void load_throwsForUnknownId() {
        assertThatThrownBy(() -> repo.load("no-such-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_isVisibleThroughBothFindByKeyAndLoad() {
        DeliveryRecord record = pendingRecord("req-1");
        repo.save(record);

        record.markSent();
        repo.update(record);

        assertThat(repo.load(record.id()).status()).isEqualTo(notification.domain.DeliveryStatus.SENT);
        assertThat(repo.findByKey("acme", "req-1")).get()
                .extracting(DeliveryRecord::status).isEqualTo(notification.domain.DeliveryStatus.SENT);
    }

    @Test
    void runInTransaction_runsTheGivenWork() {
        AtomicInteger calls = new AtomicInteger();

        repo.runInTransaction(calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void sequentialDuplicateRequestId_secondSendFindsTheFirst() {
        // Documents the intended dedupe path for a *single-threaded* caller (DefaultNotificationService's
        // find-then-save sequence). This does NOT cover the concurrent case — that's REVIEW.md C-01,
        // a known open race (check-then-act, not atomic), deliberately not exercised here.
        DeliveryRecord first = pendingRecord("req-1");
        repo.save(first);

        var existing = repo.findByKey("acme", "req-1");

        assertThat(existing).contains(first);
    }

    private DeliveryRecord pendingRecord(String requestId) {
        return DeliveryRecord.pending("acme", requestId, "alice", ChannelType.EMAIL,
                "welcome-email", Map.of("name", "Alice"), Priority.TRANSACTIONAL, providerContext);
    }
}
